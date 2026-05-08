package io.github.gabrielivo.oficina.application.veiculo;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import io.github.gabrielivo.oficina.domain.veiculo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;
    private final ClienteRepository clienteRepository;

    public VeiculoService(VeiculoRepository veiculoRepository, ClienteRepository clienteRepository) {
        this.veiculoRepository = veiculoRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    public Veiculo criar(CriarVeiculoCommand command) {
        Cliente cliente = clienteRepository.findById(command.clienteId())
            .orElseThrow(() -> new ClienteException("Cliente não encontrado: " + command.clienteId()));

        if (veiculoRepository.existsByPlaca(command.placa())) {
            throw new VeiculoException("Placa já cadastrada: " + command.placa());
        }

        Veiculo veiculo = new Veiculo(
            cliente,
            command.placa(),
            command.marca(),
            command.modelo(),
            command.ano()
        );

        return veiculoRepository.save(veiculo);
    }

    @Transactional(readOnly = true)
    public Veiculo buscarPorId(String id) {
        return veiculoRepository.findById(id)
            .orElseThrow(() -> new VeiculoException("Veículo não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Veiculo> listarTodos() {
        return veiculoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Veiculo> listarPorCliente(String clienteId) {
        return veiculoRepository.findByClienteId(clienteId);
    }

    @Transactional
    public Veiculo atualizar(String id, AtualizarVeiculoCommand command) {
        Veiculo veiculo = buscarPorId(id);
        veiculo.atualizar(command.marca(), command.modelo(), command.ano());
        return veiculoRepository.save(veiculo);
    }

    @Transactional
    public void deletar(String id) {
        Veiculo veiculo = buscarPorId(id);
        veiculoRepository.delete(veiculo);
    }
}