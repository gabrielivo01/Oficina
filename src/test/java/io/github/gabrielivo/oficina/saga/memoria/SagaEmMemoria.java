package io.github.gabrielivo.oficina.saga.memoria;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import io.github.gabrielivo.oficina.application.mensageria.OutboxService;
import io.github.gabrielivo.oficina.application.mensageria.ProcessadorMensagensService;
import io.github.gabrielivo.oficina.application.mensageria.PublicacaoOutboxService;
import io.github.gabrielivo.oficina.application.ordemServico.AbrirOrdemServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.ManipuladorEventoSaga;
import io.github.gabrielivo.oficina.application.ordemServico.NotificacaoStatusOrdemServicoManipulador;
import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.application.ordemServico.OrquestradorSagaService;
import io.github.gabrielivo.oficina.application.ordemServico.PublicadorEventosOrdemServico;
import io.github.gabrielivo.oficina.application.ordemServico.SagaProperties;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutbox;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutboxRepository;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemProcessada;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemProcessadaRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.EtapaSaga;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.saga.SituacaoSaga;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Limit;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saga completa em memória (Etapa 5.4): os serviços reais de aplicação do OS Service (abertura da OS, orquestrador,
 * outbox, publicação, inbox e manipuladores) ligados a repositórios em memória e a um {@link BarramentoEmMemoria} com
 * participantes falsos de Execution e Billing. Sem Spring, banco nem Docker. Usada pelos testes da Saga e pelo BDD.
 *
 * <p>Cada ação do teste termina em {@link #drenar()}: publica o outbox, entrega os comandos aos participantes e
 * processa os eventos de volta até não sobrar mensagem, como o broker faria.
 */
public final class SagaEmMemoria {

    private static final int MAX_CICLOS = 100;

    private final RepositorioEmMemoria<OrdemServico> ordens = RepositorioEmMemoria.porGetId();
    private final RepositorioEmMemoria<SagaOrdemServico> sagas = RepositorioEmMemoria.porId(SagaOrdemServico::getOsId);
    private final RepositorioEmMemoria<MensagemOutbox> outbox = RepositorioEmMemoria.porGetId();
    private final RepositorioEmMemoria<Cliente> clientes = RepositorioEmMemoria.porGetId();
    private final RepositorioEmMemoria<Veiculo> veiculos = RepositorioEmMemoria.porGetId();

    private final List<String> notificacoes = new ArrayList<>();
    private final BarramentoEmMemoria barramento;
    private final ExecutionFalso execution;
    private final BillingFalso billing;
    private final OrdemServicoService ordemServicoService;
    private final OrquestradorSagaService orquestrador;
    private final PublicacaoOutboxService publicacaoOutbox;
    private final ProcessadorMensagensService processador;
    private boolean entregarEventosEmDuplicidade;

    public SagaEmMemoria() {
        this(new SagaProperties(Duration.ofMinutes(30), Duration.ofSeconds(30), Duration.ofSeconds(30), 3));
    }

    public SagaEmMemoria(SagaProperties sagaProperties) {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MensageriaProperties mensageria = new MensageriaProperties(
                BarramentoEmMemoria.EXCHANGE_EVENTOS,
                BarramentoEmMemoria.EXCHANGE_COMANDOS,
                "os.saga.eventos",
                new MensageriaProperties.Outbox(true, Duration.ofSeconds(1), 50, Duration.ofSeconds(5)));

        CatalogoContratos catalogo = new CatalogoContratos(jsonMapper);
        barramento = new BarramentoEmMemoria(jsonMapper);
        execution = new ExecutionFalso(catalogo, barramento::publicarEvento);
        billing = new BillingFalso(catalogo, barramento::publicarEvento);
        barramento.registrar(execution);
        barramento.registrar(billing);

        OrdemServicoRepository ordemServicoRepository = ordens.como(OrdemServicoRepository.class);
        SagaOrdemServicoRepository sagaRepository = sagas.comConsulta(
                        "findTop100BySituacaoAndPrazoExpiracaoLessThanEqualOrderByPrazoExpiracaoAsc",
                        (registros, args) -> registros.stream()
                                .filter(saga -> saga.getSituacao() == args[0])
                                .filter(saga -> saga.getPrazoExpiracao() != null
                                        && !saga.getPrazoExpiracao().isAfter((Instant) args[1]))
                                .sorted(Comparator.comparing(SagaOrdemServico::getPrazoExpiracao))
                                .limit(100)
                                .toList())
                .comConsulta(
                        "findTop100ByDiagnosticoSolicitadoFalseAndSituacaoAndEtapaAtualOrderByCriadoEmAsc",
                        (registros, args) -> registros.stream()
                                .filter(saga -> !saga.isDiagnosticoSolicitado())
                                .filter(saga -> saga.getSituacao() == args[0] && saga.getEtapaAtual() == args[1])
                                .sorted(Comparator.comparing(SagaOrdemServico::getCriadoEm))
                                .limit(100)
                                .toList())
                .como(SagaOrdemServicoRepository.class);
        MensagemOutboxRepository outboxRepository = outbox.comConsulta(
                        "findByEnviadoEmIsNullOrderByCriadoEmAscIdAsc",
                        (registros, args) -> registros.stream()
                                .filter(mensagem -> mensagem.getEnviadoEm() == null)
                                .limit(((Limit) args[0]).isLimited() ? ((Limit) args[0]).max() : Long.MAX_VALUE)
                                .toList())
                .comConsulta(
                        "countByEnviadoEmIsNull",
                        (registros, args) -> registros.stream()
                                .filter(mensagem -> mensagem.getEnviadoEm() == null)
                                .count())
                .como(MensagemOutboxRepository.class);
        MensagemProcessadaRepository inbox =
                RepositorioEmMemoria.<MensagemProcessada>porGetId().como(MensagemProcessadaRepository.class);

        OutboxService outboxService = new OutboxService(outboxRepository, jsonMapper, () -> null);
        PublicadorEventosOrdemServico publicadorStatus =
                new PublicadorEventosOrdemServico(ordemServicoRepository, outboxService, mensageria);
        orquestrador = new OrquestradorSagaService(
                sagaRepository,
                ordemServicoRepository,
                outboxService,
                publicadorStatus,
                mensageria,
                sagaProperties,
                meterRegistry,
                jsonMapper);
        ordemServicoService = new OrdemServicoService(
                ordemServicoRepository,
                clientes.como(ClienteRepository.class),
                veiculos.como(VeiculoRepository.class),
                orquestrador,
                publicadorStatus,
                meterRegistry);
        publicacaoOutbox = new PublicacaoOutboxService(outboxService, barramento, mensageria, meterRegistry);
        processador = new ProcessadorMensagensService(
                inbox,
                List.of(
                        new ManipuladorEventoSaga(orquestrador),
                        new NotificacaoStatusOrdemServicoManipulador(
                                (osId, status, mensagem) -> notificacoes.add(osId + ":" + status), jsonMapper)));
    }

    /** Entrega cada evento duas vezes ao OS Service (redelivery do broker), para exercitar o inbox. */
    public void entregarEventosEmDuplicidade() {
        this.entregarEventosEmDuplicidade = true;
    }

    /** Cadastra um cliente com e-mail e um veículo e abre a OS, como {@code POST /ordens-servico}. */
    public String abrirOrdemServico() {
        Cliente cliente = new Cliente("52998224725", "Cliente da Saga", "11999999999", "cliente@example.com", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1D23", "Fiat", "Uno", 2020);
        clientes.como(ClienteRepository.class).save(cliente);
        veiculos.como(VeiculoRepository.class).save(veiculo);
        String osId = ordemServicoService
                .abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId()))
                .getId();
        drenar();
        return osId;
    }

    /** O mecânico inicia e conclui o diagnóstico no Execution. */
    public void concluirDiagnostico(String osId) {
        execution.concluirDiagnostico(osId);
        drenar();
    }

    /** O cliente (ou atendente) responde o orçamento no Billing. */
    public void responderOrcamento(String osId, boolean aprovado) {
        billing.responderOrcamento(osId, aprovado);
        drenar();
    }

    /** O gateway confirma o pagamento da cobrança. */
    public void confirmarPagamento(String osId) {
        billing.confirmarPagamento(osId);
        drenar();
    }

    /** O gateway rejeita o pagamento da cobrança. */
    public void rejeitarPagamento(String osId) {
        billing.rejeitarPagamento(osId);
        drenar();
    }

    /** O mecânico finaliza o reparo no Execution. */
    public void finalizarExecucao(String osId) {
        execution.finalizarExecucao(osId);
        drenar();
    }

    public void cancelar(String osId, String motivo) {
        ordemServicoService.cancelar(osId, motivo);
        drenar();
    }

    public void entregar(String osId) {
        ordemServicoService.entregar(osId);
        drenar();
    }

    /** Roda o job de prazos da Saga (pagamento expirado e nova tentativa de compensação). */
    public void verificarPrazos() {
        orquestrador.processarExpiradas();
        drenar();
    }

    /** Processa as mensagens em trânsito até o sistema ficar parado. */
    public void drenar() {
        for (int ciclo = 0; ciclo < MAX_CICLOS; ciclo++) {
            int publicadas = publicacaoOutbox.publicarPendentes();
            boolean processouEvento = false;
            EnvelopeMensagem evento;
            while ((evento = barramento.proximoEvento()) != null) {
                processador.processar(evento);
                if (entregarEventosEmDuplicidade) {
                    processador.processar(evento);
                }
                processouEvento = true;
            }
            if (publicadas == 0 && !processouEvento) {
                return;
            }
        }
        throw new IllegalStateException("A Saga em memória não estabilizou após " + MAX_CICLOS + " ciclos.");
    }

    public ExecutionFalso execution() {
        return execution;
    }

    public BillingFalso billing() {
        return billing;
    }

    public BarramentoEmMemoria barramento() {
        return barramento;
    }

    public StatusOrdemServico status(String osId) {
        return ordem(osId).getStatus();
    }

    public OrdemServico ordem(String osId) {
        return ordemServicoService.buscarPorId(osId);
    }

    public SagaOrdemServico saga(String osId) {
        return orquestrador.consultar(osId);
    }

    public SituacaoSaga situacao(String osId) {
        return saga(osId).getSituacao();
    }

    public EtapaSaga etapa(String osId) {
        return saga(osId).getEtapaAtual();
    }

    /** Notificações de status enviadas ao cliente ({@code osId:STATUS}), na ordem. */
    public List<String> notificacoes(String osId) {
        return notificacoes.stream()
                .filter(notificacao -> notificacao.startsWith(osId + ":"))
                .map(notificacao -> notificacao.substring(osId.length() + 1))
                .toList();
    }

    public long mensagensPendentesNoOutbox() {
        return outbox.registros().stream()
                .filter(mensagem -> Objects.isNull(mensagem.getEnviadoEm()))
                .count();
    }
}
