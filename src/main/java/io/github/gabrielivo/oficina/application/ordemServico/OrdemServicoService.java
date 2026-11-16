package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.*;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoException;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
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
    private final OrquestradorSagaService orquestradorSaga;
    private final PublicadorEventosOrdemServico publicadorStatus;
    private final MeterRegistry meterRegistry;

    public OrdemServicoService(
            OrdemServicoRepository ordemServicoRepository,
            ClienteRepository clienteRepository,
            VeiculoRepository veiculoRepository,
            OrquestradorSagaService orquestradorSaga,
            PublicadorEventosOrdemServico publicadorStatus,
            MeterRegistry meterRegistry) {
        this.ordemServicoRepository = ordemServicoRepository;
        this.clienteRepository = clienteRepository;
        this.veiculoRepository = veiculoRepository;
        this.orquestradorSaga = orquestradorSaga;
        this.publicadorStatus = publicadorStatus;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public OrdemServico abrir(AbrirOrdemServicoCommand command) {
        Cliente cliente = clienteRepository
                .findById(command.clienteId())
                .orElseThrow(() -> new ClienteException("Cliente não encontrado: " + command.clienteId()));
        if (cliente.getEmail() == null || cliente.getEmail().isBlank()) {
            throw new OrdemServicoException("O cliente deve informar um e-mail antes de abrir uma ordem de serviço.");
        }

        Veiculo veiculo = veiculoRepository
                .findById(command.veiculoId())
                .orElseThrow(() -> new VeiculoException("Veículo não encontrado: " + command.veiculoId()));

        OrdemServico os = new OrdemServico(cliente, veiculo);
        OrdemServico osSalva = ordemServicoRepository.save(os);
        orquestradorSaga.iniciar(osSalva);

        meterRegistry.counter("oficina.ordens_servico.abertas").increment();
        return osSalva;
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

    private void registrarTempoNoStatus(StatusOrdemServico statusAnterior, LocalDateTime statusDesde) {
        meterRegistry
                .timer("oficina.ordens_servico.tempo_no_status", "status", statusAnterior.name())
                .record(Duration.between(statusDesde, LocalDateTime.now()));
    }

    /** Solicita o cancelamento e aguarda a conclusão das compensações da Saga. */
    @Transactional
    public OrdemServico cancelar(String id, String motivo) {
        OrdemServico os = buscarPorId(id);
        if (!os.getStatus().podeTransicionarPara(StatusOrdemServico.CANCELADA)) {
            throw new OrdemServicoException("Não é possível cancelar uma OS com status " + os.getStatus() + ".");
        }
        return orquestradorSaga.solicitarCancelamento(id, motivo);
    }

    @Transactional
    public OrdemServico entregar(String id) {
        OrdemServico os = buscarPorId(id);
        StatusOrdemServico anterior = os.getStatus();
        LocalDateTime statusDesde = os.getStatusDesde();
        if (anterior != StatusOrdemServico.FINALIZADA) {
            throw new OrdemServicoException("Somente uma OS finalizada pode ser entregue.");
        }
        os.entregar();
        registrarTempoNoStatus(anterior, statusDesde);
        return publicadorStatus.salvarEPublicar(os);
    }

    @Transactional(readOnly = true)
    public SagaOrdemServico consultarSaga(String id) {
        buscarPorId(id);
        return orquestradorSaga.consultar(id);
    }
}
