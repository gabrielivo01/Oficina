package io.github.gabrielivo.oficina.application.ordemServico;


import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import io.github.gabrielivo.oficina.domain.ordemServico.*;
import io.github.gabrielivo.oficina.domain.peca.Peca;
import io.github.gabrielivo.oficina.domain.peca.PecaException;
import io.github.gabrielivo.oficina.domain.peca.PecaRepository;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoException;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import io.github.gabrielivo.oficina.infrastructure.notification.StatusNotificationPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class OrdemServicoService {

    private final OrdemServicoRepository ordemServicoRepository;
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;
    private final PecaRepository pecaRepository;
    private final StatusNotificationPort statusNotificationPort;
    private final MeterRegistry meterRegistry;

    public OrdemServicoService(
        OrdemServicoRepository ordemServicoRepository,
        ClienteRepository clienteRepository,
        VeiculoRepository veiculoRepository,
        PecaRepository pecaRepository,
        @Autowired(required = false) StatusNotificationPort statusNotificationPort,
        MeterRegistry meterRegistry
    ) {
        this.ordemServicoRepository = ordemServicoRepository;
        this.clienteRepository = clienteRepository;
        this.veiculoRepository = veiculoRepository;
        this.pecaRepository = pecaRepository;
        this.statusNotificationPort = statusNotificationPort;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public OrdemServico abrir(AbrirOrdemServicoCommand command) {
        Cliente cliente = clienteRepository.findById(command.clienteId())
            .orElseThrow(() -> new ClienteException("Cliente não encontrado: " + command.clienteId()));

        Veiculo veiculo = veiculoRepository.findById(command.veiculoId())
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
        return ordemServicoRepository.findById(id)
            .orElseThrow(() -> new OrdemServicoException("Ordem de Serviço não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrdemServico> listarTodas() {
        return ordemServicoRepository.findAll().stream()
            .filter(os -> os.getStatus() != StatusOrdemServico.FINALIZADA && os.getStatus() != StatusOrdemServico.ENTREGUE)
            .sorted(Comparator.comparingInt((OrdemServico os) -> prioridadeStatus(os.getStatus()))
                .thenComparing(OrdemServico::getCriadoEm))
            .toList();
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
                Peca peca = pecaRepository.findById(command.pecaId())
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
        OrdemServico osAtualizada = ordemServicoRepository.save(os);
        if (statusNotificationPort != null) {
            statusNotificationPort.enviarAtualizacao(osAtualizada.getId(), osAtualizada.getStatus(), "Status da OS atualizado");
        }
        return osAtualizada;
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
        OrdemServico osAtualizada = ordemServicoRepository.save(os);
        if (statusNotificationPort != null) {
            statusNotificationPort.enviarAtualizacao(osAtualizada.getId(), osAtualizada.getStatus(), "Orçamento respondido");
        }
        return osAtualizada;
    }

    @Transactional
    public OrdemServico atualizarStatusExterno(String id, StatusOrdemServico novoStatus) {
        OrdemServico os = buscarPorId(id);
        StatusOrdemServico statusAnterior = os.getStatus();
        LocalDateTime statusDesde = os.getStatusDesde();
        os.atualizarStatusExterno(novoStatus);
        registrarTempoNoStatus(statusAnterior, statusDesde);
        OrdemServico osAtualizada = ordemServicoRepository.save(os);
        if (statusNotificationPort != null) {
            statusNotificationPort.enviarAtualizacao(osAtualizada.getId(), osAtualizada.getStatus(), "Status atualizado via integração externa");
        }
        return osAtualizada;
    }

    private void registrarTempoNoStatus(StatusOrdemServico statusAnterior, LocalDateTime statusDesde) {
        meterRegistry.timer("oficina.ordens_servico.tempo_no_status", "status", statusAnterior.name())
            .record(Duration.between(statusDesde, LocalDateTime.now()));
    }

    @Transactional
    public OrdemServico cancelar(String id) {
        OrdemServico os = buscarPorId(id);

        if (os.getStatus() == StatusOrdemServico.FINALIZADA || os.getStatus() == StatusOrdemServico.ENTREGUE) {
            throw new OrdemServicoException("Não é possível cancelar uma OS finalizada ou entregue.");
        }

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

        return ordemServicoRepository.save(os);
    }

    @Transactional(readOnly = true)
    public List<Peca> verificarAlertasEstoque() {
        return pecaRepository.findAll().stream()
            .filter(Peca::estoqueEstaBaixo)
            .toList();
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
                Peca peca = pecaRepository.findById(command.pecaId())
                    .orElseThrow(() -> new PecaException("Peça não encontrada: " + command.pecaId()));

                peca.reduzirEstoque(command.quantidade());
                pecaRepository.save(peca);

                yield new ItemOrdemServico(os, command.descricao(), command.valor(), command.quantidade(), peca);
            }
        };

        os.adicionarItem(item);
    }

    private int prioridadeStatus(StatusOrdemServico status) {
        return switch (status) {
            case EM_EXECUCAO -> 0;
            case AGUARDANDO_APROVACAO -> 1;
            case EM_DIAGNOSTICO -> 2;
            case RECEBIDA -> 3;
            default -> 4;
        };
    }
}
