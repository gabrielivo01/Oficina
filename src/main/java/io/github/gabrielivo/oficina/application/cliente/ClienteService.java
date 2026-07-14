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
        validarCpfDisponivel(command.cpf());

        Cliente cliente = new Cliente(
            command.cpf(),
            command.nome(),
            command.telefone(),
            criarEndereco(command.endereco())
        );

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
        cliente.atualizar(command.nome(), command.telefone(), criarEndereco(command.endereco()));
        return clienteRepository.save(cliente);
    }

    @Transactional
    public void deletar(String id) {
        Cliente cliente = buscarPorId(id);
        clienteRepository.delete(cliente);
    }

    private void validarCpfDisponivel(String cpf) {
        if (clienteRepository.existsByCpf(cpf)) {
            throw new ClienteException("CPF já cadastrado: " + cpf);
        }
    }

    private Endereco criarEndereco(EnderecoCommand command) {
        if (command == null) {
            return null;
        }

        return new Endereco(
            command.cep(),
            command.logradouro(),
            command.numero(),
            command.complemento(),
            command.bairro(),
            command.cidade(),
            command.uf()
        );
    }
}