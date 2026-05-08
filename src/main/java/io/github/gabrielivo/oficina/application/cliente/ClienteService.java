package io.github.gabrielivo.oficina.application.cliente;

import io.github.gabrielivo.oficina.domain.cliente.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    public Cliente criar(CriarClienteCommand command) {
        if (clienteRepository.existsByCpf(command.cpf())) {
            throw new ClienteException("CPF já cadastrado: " + command.cpf());
        }

        Endereco endereco = null;
        if (command.endereco() != null) {
            var e = command.endereco();
            endereco = new Endereco(
                e.cep(), e.logradouro(), e.numero(),
                e.complemento(), e.bairro(), e.cidade(), e.uf()
            );
        }

        Cliente cliente = new Cliente(command.cpf(), command.nome(), command.telefone(), endereco);
        return clienteRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(String id) {
        return clienteRepository.findById(id)
            .orElseThrow(() -> new ClienteException("Cliente não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    @Transactional
    public Cliente atualizar(String id, AtualizarClienteCommand command) {
        Cliente cliente = buscarPorId(id);

        Endereco endereco = null;
        if (command.endereco() != null) {
            var e = command.endereco();
            endereco = new Endereco(
                e.cep(), e.logradouro(), e.numero(),
                e.complemento(), e.bairro(), e.cidade(), e.uf()
            );
        }

        cliente.atualizar(command.nome(), command.telefone(), endereco);
        return clienteRepository.save(cliente);
    }

    @Transactional
    public void deletar(String id) {
        Cliente cliente = buscarPorId(id);
        clienteRepository.delete(cliente);
    }
}