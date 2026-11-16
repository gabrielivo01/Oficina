package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import io.github.gabrielivo.oficina.application.mensageria.OutboxService;
import io.github.gabrielivo.oficina.domain.ordemServico.ItemOrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoException;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.ComandoSaga;
import io.github.gabrielivo.oficina.domain.saga.DecisaoSaga;
import io.github.gabrielivo.oficina.domain.saga.EtapaSaga;
import io.github.gabrielivo.oficina.domain.saga.EventoSaga;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.saga.SituacaoSaga;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OrquestradorSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrquestradorSagaService.class);
    private static final String FILA_EXECUTION = "execution.comandos";
    private static final String FILA_BILLING = "billing.comandos";

    private final SagaOrdemServicoRepository sagaRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final OutboxService outboxService;
    private final PublicadorEventosOrdemServico publicadorStatus;
    private final MensageriaProperties mensageria;
    private final SagaProperties properties;
    private final MeterRegistry meterRegistry;
    private final JsonMapper jsonMapper;

    public OrquestradorSagaService(
            SagaOrdemServicoRepository sagaRepository,
            OrdemServicoRepository ordemServicoRepository,
            OutboxService outboxService,
            PublicadorEventosOrdemServico publicadorStatus,
            MensageriaProperties mensageria,
            SagaProperties properties,
            MeterRegistry meterRegistry,
            JsonMapper jsonMapper) {
        this.sagaRepository = sagaRepository;
        this.ordemServicoRepository = ordemServicoRepository;
        this.outboxService = outboxService;
        this.publicadorStatus = publicadorStatus;
        this.mensageria = mensageria;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void iniciar(OrdemServico os) {
        Instant agora = Instant.now();
        SagaOrdemServico saga = sagaRepository.save(new SagaOrdemServico(os.getId(), agora));
        DecisaoSaga decisao = saga.iniciar(agora);
        publicarComandos(saga, os, decisao.comandos(), Map.of(), agora);
        meterRegistry.counter("oficina.sagas.iniciadas").increment();
    }

    @Transactional
    public void processar(EnvelopeMensagem envelope) {
        if (envelope == null
                || envelope.osId() == null
                || envelope.osId().isBlank()
                || !envelope.osId().equals(envelope.sagaId())
                || envelope.payload() == null) {
            throw new IllegalArgumentException("Evento da Saga deve identificar uma OS e conter payload.");
        }
        SagaOrdemServico saga = buscarSaga(envelope.osId());
        OrdemServico os = buscarOrdem(envelope.osId());
        Map<String, Object> payload = converterPayload(envelope.payload());
        EventoSaga evento = new EventoSaga(envelope.messageId(), envelope.type(), envelope.occurredAt(), payload);
        EtapaSaga etapaAnterior = saga.getEtapaAtual();
        SituacaoSaga situacaoAnterior = saga.getSituacao();
        Instant atualizadoAnterior = saga.getAtualizadoEm();
        Instant agora = Instant.now();

        DecisaoSaga decisao = saga.aoReceber(evento, agora, properties.prazoPagamento(), properties.prazoCompensacao());
        if (decisao.aplicada() && "DiagnosticoConcluido".equals(envelope.type())) {
            registrarSnapshotDiagnostico(os, payload);
        }
        if (decisao.novoStatus() != null && os.getStatus() != decisao.novoStatus()) {
            alterarStatusPorSaga(os, decisao.novoStatus(), envelope.type(), payload);
            publicadorStatus.salvarEPublicar(os);
        }
        publicarComandos(saga, os, decisao.comandos(), payload, agora);
        registrarMetricas(saga, etapaAnterior, situacaoAnterior, atualizadoAnterior, agora, decisao.aplicada());
        sagaRepository.save(saga);
    }

    @Transactional
    public OrdemServico solicitarCancelamento(String osId, String motivo) {
        SagaOrdemServico saga = buscarSaga(osId);
        OrdemServico os = buscarOrdem(osId);
        Instant agora = Instant.now();
        DecisaoSaga decisao = saga.solicitarCancelamento(motivo, agora, properties.prazoCompensacao());
        sagaRepository.save(saga);
        publicarComandos(saga, os, decisao.comandos(), Map.of(), agora);
        return os;
    }

    @Transactional
    public SagaOrdemServico consultar(String osId) {
        return buscarSaga(osId);
    }

    @Transactional
    public void processarExpiradas() {
        Instant agora = Instant.now();
        List<SagaOrdemServico> expiradas = new ArrayList<>();
        expiradas.addAll(sagaRepository.findTop100BySituacaoAndPrazoExpiracaoLessThanEqualOrderByPrazoExpiracaoAsc(
                SituacaoSaga.EM_ANDAMENTO, agora));
        expiradas.addAll(sagaRepository.findTop100BySituacaoAndPrazoExpiracaoLessThanEqualOrderByPrazoExpiracaoAsc(
                SituacaoSaga.COMPENSANDO, agora));
        for (SagaOrdemServico saga : expiradas) {
            processarExpiracao(saga, agora);
        }
    }

    @Transactional
    public void iniciarDiagnosticosPendentes() {
        List<SagaOrdemServico> pendentes =
                sagaRepository.findTop100ByDiagnosticoSolicitadoFalseAndSituacaoAndEtapaAtualOrderByCriadoEmAsc(
                        SituacaoSaga.EM_ANDAMENTO, EtapaSaga.AGUARDANDO_DIAGNOSTICO_INICIADO);
        for (SagaOrdemServico saga : pendentes) {
            OrdemServico os = buscarOrdem(saga.getOsId());
            Instant agora = Instant.now();
            DecisaoSaga decisao = saga.iniciar(agora);
            publicarComandos(saga, os, decisao.comandos(), Map.of(), agora);
            sagaRepository.save(saga);
        }
    }

    private void processarExpiracao(SagaOrdemServico saga, Instant agora) {
        OrdemServico os = buscarOrdem(saga.getOsId());
        EtapaSaga etapaAnterior = saga.getEtapaAtual();
        SituacaoSaga situacaoAnterior = saga.getSituacao();
        Instant atualizadoAnterior = saga.getAtualizadoEm();
        DecisaoSaga decisao = situacaoAnterior == SituacaoSaga.EM_ANDAMENTO
                ? saga.expirarPagamento(agora, properties.prazoCompensacao())
                : saga.repetirCompensacao(agora, properties.prazoCompensacao(), properties.maxTentativasCompensacao());
        if (!decisao.aplicada()) {
            return;
        }
        publicarComandos(saga, os, decisao.comandos(), Map.of(), agora);
        registrarMetricas(saga, etapaAnterior, situacaoAnterior, atualizadoAnterior, agora, true);
        if (saga.getSituacao() == SituacaoSaga.FALHA_COMPENSACAO) {
            log.error(
                    "Saga da OS {} entrou em FALHA_COMPENSACAO na etapa {}: {}",
                    saga.getOsId(),
                    saga.getEtapaAtual(),
                    saga.getFalhaCompensacao());
        }
        sagaRepository.save(saga);
    }

    private void publicarComandos(
            SagaOrdemServico saga,
            OrdemServico os,
            List<ComandoSaga> comandos,
            Map<String, Object> payloadEvento,
            Instant agora) {
        for (ComandoSaga comando : comandos) {
            Map<String, Object> payload = enriquecerPayload(comando, os, payloadEvento);
            String destino = destino(comando.tipo());
            outboxService.registrar(
                    mensageria.exchangeComandos(), destino, comando.tipo(), saga.getOsId(), agora, payload);
        }
    }

    private Map<String, Object> enriquecerPayload(
            ComandoSaga comando, OrdemServico os, Map<String, Object> payloadEvento) {
        Map<String, Object> payload = new LinkedHashMap<>(comando.payload());
        switch (comando.tipo()) {
            case "SolicitarDiagnostico" -> payload.putAll(payloadSolicitarDiagnostico(os));
            case "GerarOrcamento" -> {
                payload.put("cliente", payloadCliente(os));
                payload.put("veiculo", payloadVeiculo(os, false));
            }
            case "CriarCobranca" -> {
                String descricao = os.getItens().stream()
                        .findFirst()
                        .map(ItemOrdemServico::getDescricao)
                        .orElse("serviços automotivos");
                payload.put(
                        "descricao",
                        "OS " + os.getId().substring(0, Math.min(os.getId().length(), 8)) + " - " + descricao);
            }
            case "IniciarExecucao" -> payload.put("placa", os.getVeiculo().getPlaca());
            default -> {}
        }
        return payload;
    }

    private Map<String, Object> payloadSolicitarDiagnostico(OrdemServico os) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clienteId", os.getCliente().getId());
        payload.put("veiculo", payloadVeiculo(os, true));
        return payload;
    }

    private Map<String, Object> payloadCliente(OrdemServico os) {
        if (os.getCliente().getEmail() == null || os.getCliente().getEmail().isBlank()) {
            throw new IllegalStateException(
                    "O cliente deve informar um e-mail válido antes de gerar o orçamento da OS " + os.getId() + ".");
        }
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("id", os.getCliente().getId());
        cliente.put("nome", os.getCliente().getNome());
        cliente.put("cpf", os.getCliente().getCpf());
        cliente.put("telefone", os.getCliente().getTelefone());
        cliente.put("email", os.getCliente().getEmail());
        return cliente;
    }

    private Map<String, Object> payloadVeiculo(OrdemServico os, boolean incluirId) {
        Map<String, Object> veiculo = new LinkedHashMap<>();
        if (incluirId) {
            veiculo.put("id", os.getVeiculo().getId());
        }
        veiculo.put("placa", os.getVeiculo().getPlaca());
        veiculo.put("marca", os.getVeiculo().getMarca());
        veiculo.put("modelo", os.getVeiculo().getModelo());
        if (os.getVeiculo().getAno() != null) {
            veiculo.put("ano", os.getVeiculo().getAno());
        }
        return veiculo;
    }

    private void registrarSnapshotDiagnostico(OrdemServico os, Map<String, Object> payload) {
        Object itensPayload = payload.get("itens");
        if (!(itensPayload instanceof List<?> itens) || itens.isEmpty()) {
            throw new IllegalArgumentException("DiagnosticoConcluido deve conter pelo menos um item.");
        }
        List<ItemOrdemServico> itensSnapshot =
                itens.stream().map(item -> criarItemSnapshot(os, item)).toList();
        os.registrarSnapshotDiagnostico(itensSnapshot);
        BigDecimal totalInformado = decimal(payload.get("valorTotal"));
        if (os.getValorTotal().compareTo(totalInformado) != 0) {
            throw new IllegalArgumentException("O valorTotal do diagnóstico não corresponde à soma dos itens.");
        }
    }

    private ItemOrdemServico criarItemSnapshot(OrdemServico os, Object itemPayload) {
        if (!(itemPayload instanceof Map<?, ?> item)) {
            throw new IllegalArgumentException("Cada item do diagnóstico deve ser um objeto.");
        }
        String tipo = texto(item, "tipo");
        String descricao = texto(item, "descricao");
        BigDecimal valorUnitario = decimal(item.get("valorUnitario"));
        if ("SERVICO".equals(tipo)) {
            return new ItemOrdemServico(os, descricao, valorUnitario);
        }
        if ("PECA".equals(tipo)) {
            String pecaId = texto(item, "pecaId");
            Object quantidadeValue = item.get("quantidade");
            if (!(quantidadeValue instanceof Number quantidade)) {
                throw new IllegalArgumentException("Quantidade do item de peça deve ser um número.");
            }
            return new ItemOrdemServico(os, descricao, valorUnitario, quantidade.intValue(), pecaId, descricao);
        }
        throw new IllegalArgumentException("Tipo de item de diagnóstico inválido: " + tipo);
    }

    private void alterarStatusPorSaga(
            OrdemServico os, StatusOrdemServico novoStatus, String tipoEvento, Map<String, Object> payload) {
        String motivo = textoOpcional(payload, "motivo");
        if (motivo == null) {
            motivo = tipoEvento;
        }
        if (novoStatus == StatusOrdemServico.CANCELADA) {
            os.cancelar(OrigemMudancaStatus.SAGA, motivo);
        } else {
            os.alterarStatus(novoStatus, OrigemMudancaStatus.SAGA, motivo);
        }
    }

    private void registrarMetricas(
            SagaOrdemServico saga,
            EtapaSaga etapaAnterior,
            SituacaoSaga situacaoAnterior,
            Instant atualizadoAnterior,
            Instant agora,
            boolean aplicada) {
        if (!aplicada) {
            return;
        }
        if (etapaAnterior != saga.getEtapaAtual() && atualizadoAnterior != null) {
            Timer.builder("oficina.saga.duracao.etapa")
                    .tag("etapa", etapaAnterior.name())
                    .register(meterRegistry)
                    .record(Duration.between(atualizadoAnterior, agora));
        }
        if (situacaoAnterior != saga.getSituacao()) {
            switch (saga.getSituacao()) {
                case CONCLUIDA ->
                    meterRegistry.counter("oficina.sagas.concluidas").increment();
                case COMPENSADA ->
                    meterRegistry.counter("oficina.sagas.compensadas").increment();
                case FALHA_COMPENSACAO ->
                    meterRegistry.counter("oficina.sagas.falha_compensacao").increment();
                default -> {}
            }
        }
    }

    private String destino(String tipoComando) {
        return switch (tipoComando) {
            case "GerarOrcamento", "CriarCobranca", "CancelarCobranca", "EstornarPagamento" -> FILA_BILLING;
            case "SolicitarDiagnostico", "IniciarExecucao", "CancelarExecucao", "LiberarReservaPecas" -> FILA_EXECUTION;
            default -> throw new IllegalArgumentException("Tipo de comando da Saga sem destino: " + tipoComando);
        };
    }

    private Map<String, Object> converterPayload(JsonNode payload) {
        Map<?, ?> bruto = jsonMapper.treeToValue(payload, Map.class);
        Map<String, Object> convertido = new LinkedHashMap<>();
        bruto.forEach((chave, valor) -> convertido.put((String) chave, valor));
        return convertido;
    }

    private SagaOrdemServico buscarSaga(String osId) {
        return sagaRepository
                .findById(osId)
                .orElseThrow(() -> new OrdemServicoException("Saga não encontrada para a OS: " + osId));
    }

    private OrdemServico buscarOrdem(String osId) {
        return ordemServicoRepository
                .findById(osId)
                .orElseThrow(() -> new OrdemServicoException("Ordem de Serviço não encontrada: " + osId));
    }

    private static BigDecimal decimal(Object valor) {
        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }
        if (valor instanceof Number numero) {
            return new BigDecimal(numero.toString());
        }
        throw new IllegalArgumentException("Valor monetário inválido no evento.");
    }

    private static String texto(Map<?, ?> payload, String campo) {
        String valor = textoOpcional(payload, campo);
        if (valor == null) {
            throw new IllegalArgumentException("Campo obrigatório ausente no evento: " + campo);
        }
        return valor;
    }

    private static String textoOpcional(Map<?, ?> payload, String campo) {
        Object valor = payload.get(campo);
        return valor instanceof String texto && !texto.isBlank() ? texto : null;
    }
}
