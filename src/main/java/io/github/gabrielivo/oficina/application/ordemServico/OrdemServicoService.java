package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import io.github.gabrielivo.oficina.application.mensageria.OutboxService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.*;
import io.github.gabrielivo.oficina.domain.peca.Peca;
import io.github.gabrielivo.oficina.domain.peca.PecaException;
import io.github.gabrielivo.oficina.domain.peca.PecaRepository;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoException;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdemServicoService {

    private final OrdemServicoRepository ordemServicoRepository;
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;
    private final PecaRepository pecaRepository;
    private final OutboxService outboxService;
    private final MensageriaProperties mensageria;
    private final MeterRegistry meterRegistry;

    public OrdemServicoService(
            OrdemServicoRepository ordemServicoRepository,
            ClienteRepository clienteRepository,
            VeiculoRepository veiculoRepository,
            PecaRepository pecaRepository,
            OutboxService outboxService,
            MensageriaProperties mensageria,
            MeterRegistry meterRegistry) {
        this.ordemServicoRepository = ordemServicoRepository;
        this.clienteRepository = clienteRepository;
        this.veiculoRepository = veiculoRepository;
        this.pecaRepository = pecaRepository;
        this.outboxService = outboxService;
        this.mensageria = mensageria;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public OrdemServico abrir(AbrirOrdemServicoCommand command) {
        Cliente cliente = clienteRepository
                .findById(command.clienteId())
                .orElseThrow(() -> new ClienteException("Cliente não encontrado: " + command.clienteId()));

        Veiculo veiculo = veiculoRepository
                .findById(command.veiculoId())
                .orElseThrow(() -> new VeiculoException("Veículo não encontrado: " + command.veiculoId()));

        OrdemServico os = new OrdemServico(cliente, veiculo);
        OrdemServico osSalva = ordemServicoRepository.save(os);

        if (command.itens() != null && !command.itens().isEmpty()) {
            for (ItemOrdemServicoCommand itemCommand : command.itens()) {
                adicionarItemInterno(osSalva, itemCommand);
            }
        }

        meterRegistry.counter("oficina.ordens_servico.abertas").increment();
        return ordemServicoRepository.save(osSalva);
    }

    @Transactional(readOnly = true)
    public OrdemServico buscarPorId(String id) {
        return ordemServicoRepository
                .findById(id)
                .orElseThrow(() -> new OrdemServicoException("Ordem de Serviço não encontrada: " + id));
    }

    /** Paginação e filtro de status feitos no banco, ordenados por prioridade de atendimento. */
    @Transactional(readOnly = true)
    public Page<OrdemServico> listar(ListarOrdensServicoCommand command) {
        return ordemServicoRepository.buscarPorStatusOrdenadoPorPrioridade(
                command.status(), PageRequest.of(command.pagina(), command.tamanho()));
    }

    @Transactional(readOnly = true)
    public List<HistoricoStatusOrdemServico> listarHistorico(String id) {
        return List.copyOf(buscarPorId(id).getHistorico());
    }

    @Transactional(readOnly = true)
    public List<OrdemServico> listarPorCliente(String clienteId) {
        return ordemServicoRepository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public List<OrdemServico> listarPorVeiculo(String veiculoId) {
        return ordemServicoRepository.findByVeiculoId(veiculoId);
    }

    @Transactional
    public OrdemServico adicionarItem(AdicionarItemOSCommand command) {
        OrdemServico os = buscarPorId(command.ordemServicoId());

        ItemOrdemServico item = switch (command.tipo()) {
            case SERVICO -> new ItemOrdemServico(os, command.descricao(), command.valor());
            case PECA -> {
                if (command.pecaId() == null) {
                    throw new OrdemServicoException("pecaId é obrigatório para itens do tipo PECA.");
                }
                if (command.quantidade() == null || command.quantidade() <= 0) {
                    throw new OrdemServicoException("Quantidade deve ser maior que zero para itens do tipo PECA.");
                }
                Peca peca = pecaRepository
                        .findById(command.pecaId())
                        .orElseThrow(() -> new PecaException("Peça não encontrada: " + command.pecaId()));

                peca.reduzirEstoque(command.quantidade());
                pecaRepository.save(peca);

                yield new ItemOrdemServico(os, command.descricao(), command.valor(), command.quantidade(), peca);
            }
        };

        os.adicionarItem(item);
        return ordemServicoRepository.save(os);
    }

    @Transactional
    public OrdemServico removerItem(String ordemServicoId, String itemId) {
        OrdemServico os = buscarPorId(ordemServicoId);

        ItemOrdemServico itemRemovido = os.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new OrdemServicoException("Item não encontrado na OS: " + itemId));

        if (itemRemovido.getTipo() == TipoItemOrdemServico.PECA && itemRemovido.getPeca() != null) {
            Peca peca = itemRemovido.getPeca();
            peca.reporEstoque(itemRemovido.getQuantidade());
            pecaRepository.save(peca);
        }

        os.removerItem(itemId);
        return ordemServicoRepository.save(os);
    }

    @Transactional
    public OrdemServico avancarStatus(String id) {
        OrdemServico os = buscarPorId(id);
        StatusOrdemServico statusAnterior = os.getStatus();
        LocalDateTime statusDesde = os.getStatusDesde();
        os.avancarStatus();
        registrarTempoNoStatus(statusAnterior, statusDesde);
        return salvarEPublicarEventos(os);
    }

    @Transactional
    public OrdemServico responderOrcamento(String id, boolean aprovado, String observacao) {
        OrdemServico os = buscarPorId(id);
        StatusOrdemServico statusAnterior = os.getStatus();
        LocalDateTime statusDesde = os.getStatusDesde();
        if (aprovado) {
            os.aprovarOrcamento();
        } else {
            os.recusarOrcamento();
        }
        registrarTempoNoStatus(statusAnterior, statusDesde);
        return salvarEPublicarEventos(os);
    }

    @Transactional
    public OrdemServico atualizarStatusExterno(String id, StatusOrdemServico novoStatus) {
        OrdemServico os = buscarPorId(id);
        StatusOrdemServico statusAnterior = os.getStatus();
        LocalDateTime statusDesde = os.getStatusDesde();
        os.atualizarStatusExterno(novoStatus);
        registrarTempoNoStatus(statusAnterior, statusDesde);
        return salvarEPublicarEventos(os);
    }

    private void registrarTempoNoStatus(StatusOrdemServico statusAnterior, LocalDateTime statusDesde) {
        meterRegistry
                .timer("oficina.ordens_servico.tempo_no_status", "status", statusAnterior.name())
                .record(Duration.between(statusDesde, LocalDateTime.now()));
    }

    /**
     * Salva a OS e grava no outbox, na mesma transação, um {@code StatusOrdemServicoAlterado} por transição ocorrida.
     * A notificação ao cliente é feita por quem consome o evento, depois do commit.
     */
    private OrdemServico salvarEPublicarEventos(OrdemServico os) {
        OrdemServico salva = ordemServicoRepository.save(os);
        for (StatusOrdemServicoAlterado evento : os.extrairEventos()) {
            Instant alteradoEm =
                    evento.alteradoEm().atZone(ZoneId.systemDefault()).toInstant();
            outboxService.registrar(
                    mensageria.exchangeEventos(),
                    StatusOrdemServicoAlteradoPayload.ROUTING_KEY,
                    StatusOrdemServicoAlteradoPayload.TIPO,
                    evento.ordemServicoId(),
                    alteradoEm,
                    new StatusOrdemServicoAlteradoPayload(
                            evento.statusAnterior(),
                            evento.statusNovo(),
                            evento.origem(),
                            evento.motivo(),
                            alteradoEm));
        }
        return salva;
    }

    /**
     * Cancela a OS (status CANCELADA + histórico) e, enquanto o estoque ainda vive neste serviço, repõe as peças e
     * remove os itens. A compensação entre serviços vem com a Saga (Fase 5).
     */
    @Transactional
    public OrdemServico cancelar(String id, String motivo) {
        OrdemServico os = buscarPorId(id);
        StatusOrdemServico statusAnterior = os.getStatus();
        LocalDateTime statusDesde = os.getStatusDesde();

        os.cancelar(OrigemMudancaStatus.USUARIO, motivo);
        registrarTempoNoStatus(statusAnterior, statusDesde);

        // Repor o estoque de todas as peças utilizadas nesta OS
        for (ItemOrdemServico item : os.getItens()) {
            if (item.getTipo() == TipoItemOrdemServico.PECA && item.getPeca() != null) {
                Peca peca = item.getPeca();
                peca.reporEstoque(item.getQuantidade());
                pecaRepository.save(peca);
            }
        }

        // Limpar todos os itens da OS usando o método de domínio
        os.limparItens();

        return salvarEPublicarEventos(os);
    }

    @Transactional(readOnly = true)
    public List<Peca> verificarAlertasEstoque() {
        return pecaRepository.findAll().stream().filter(Peca::estoqueEstaBaixo).toList();
    }

    private void adicionarItemInterno(OrdemServico os, ItemOrdemServicoCommand command) {
        ItemOrdemServico item = switch (command.tipo()) {
            case SERVICO -> new ItemOrdemServico(os, command.descricao(), command.valor());
            case PECA -> {
                if (command.pecaId() == null) {
                    throw new OrdemServicoException("pecaId é obrigatório para itens do tipo PECA.");
                }
                if (command.quantidade() == null || command.quantidade() <= 0) {
                    throw new OrdemServicoException("Quantidade deve ser maior que zero para itens do tipo PECA.");
                }
                Peca peca = pecaRepository
                        .findById(command.pecaId())
                        .orElseThrow(() -> new PecaException("Peça não encontrada: " + command.pecaId()));

                peca.reduzirEstoque(command.quantidade());
                pecaRepository.save(peca);

                yield new ItemOrdemServico(os, command.descricao(), command.valor(), command.quantidade(), peca);
            }
        };

        os.adicionarItem(item);
    }
}
