package io.github.gabrielivo.oficina.application.cliente;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(ClienteService.class)
class ClienteServiceTest {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ClienteService clienteService;

    @Test
    void deveCriarClienteQuandoDadosSaoValidos() {
        var enderecoCommand = new EnderecoCommand(
                "12345678",
                "Rua das Flores",
                "123",
                "Apto 101",
                "Centro",
                "São Paulo",
                "SP");

        var command = new CriarClienteCommand(
                "12345678901",
                "Gabriel Ivo",
                "(11) 99999-9999",
                enderecoCommand);

        Cliente cliente = clienteService.criar(command);

        assertNotNull(cliente.getId(), "O cliente criado deve receber um id");
        assertEquals("12345678901", cliente.getCpf());
        assertEquals("Gabriel Ivo", cliente.getNome());
        assertEquals("(11) 99999-9999", cliente.getTelefone());
        assertNotNull(cliente.getEndereco(), "O cliente deve ter um endereço associado");
        assertEquals("Rua das Flores", cliente.getEndereco().getLogradouro());
    }

    @Test
    void deveBuscarClientePorIdQuandoClienteExiste() {
        var enderecoCommand = new EnderecoCommand(
                "87654321",
                "Avenida Brasil",
                "456",
                null,
                "Jardim",
                "Rio de Janeiro",
                "RJ");

        var command = new CriarClienteCommand(
                "98765432100",
                "Maria Silva",
                "(21) 88888-8888",
                enderecoCommand);

        Cliente clienteCriado = clienteService.criar(command);

        Cliente clienteBuscado = clienteService.buscarPorId(clienteCriado.getId());

        assertNotNull(clienteBuscado);
        assertEquals(clienteCriado.getId(), clienteBuscado.getId());
        assertEquals("98765432100", clienteBuscado.getCpf());
        assertEquals("Maria Silva", clienteBuscado.getNome());
        assertEquals("(21) 88888-8888", clienteBuscado.getTelefone());
        assertNotNull(clienteBuscado.getEndereco());
        assertEquals("Avenida Brasil", clienteBuscado.getEndereco().getLogradouro());
    }

    @Test
    void deveLancarExcecaoQuandoBuscarClientePorIdInexistente() {
        String idInexistente = "id-que-nao-existe";

        ClienteException exception = assertThrows(ClienteException.class, () -> {
            clienteService.buscarPorId(idInexistente);
        });

        assertEquals("Cliente não encontrado: " + idInexistente, exception.getMessage());
    }

    @Test
    void deveListarTodosOsClientes() {
        // Criar dois clientes
        var endereco1 = new EnderecoCommand("11111111", "Rua A", "1", null, "Bairro A", "Cidade A", "AA");
        var command1 = new CriarClienteCommand("11111111111", "Cliente 1", "1111-1111", endereco1);
        clienteService.criar(command1);

        var endereco2 = new EnderecoCommand("22222222", "Rua B", "2", null, "Bairro B", "Cidade B", "BB");
        var command2 = new CriarClienteCommand("22222222222", "Cliente 2", "2222-2222", endereco2);
        clienteService.criar(command2);

        var clientes = clienteService.listarTodos();

        assertNotNull(clientes);
        assertEquals(2, clientes.size());
        assertTrue(clientes.stream().anyMatch(c -> c.getNome().equals("Cliente 1")));
        assertTrue(clientes.stream().anyMatch(c -> c.getNome().equals("Cliente 2")));
    }

    @Test
    void deveAtualizarClienteQuandoDadosSaoValidos() {
        // Criar cliente
        var enderecoOriginal = new EnderecoCommand("33333333", "Rua Original", "3", null, "Bairro Original", "Cidade Original", "OO");
        var commandCriar = new CriarClienteCommand("33333333333", "Nome Original", "3333-3333", enderecoOriginal);
        Cliente clienteCriado = clienteService.criar(commandCriar);

        // Atualizar cliente
        var enderecoNovo = new EnderecoCommand("44444444", "Rua Nova", "4", "Apt 4", "Bairro Novo", "Cidade Nova", "NN");
        var commandAtualizar = new AtualizarClienteCommand("Nome Atualizado", "4444-4444", enderecoNovo);

        Cliente clienteAtualizado = clienteService.atualizar(clienteCriado.getId(), commandAtualizar);

        assertEquals(clienteCriado.getId(), clienteAtualizado.getId());
        assertEquals("33333333333", clienteAtualizado.getCpf()); // CPF não muda
        assertEquals("Nome Atualizado", clienteAtualizado.getNome());
        assertEquals("4444-4444", clienteAtualizado.getTelefone());
        assertNotNull(clienteAtualizado.getEndereco());
        assertEquals("Rua Nova", clienteAtualizado.getEndereco().getLogradouro());
        assertEquals("Apt 4", clienteAtualizado.getEndereco().getComplemento());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarClienteInexistente() {
        var commandAtualizar = new AtualizarClienteCommand("Nome", "1234-5678", null);
        String idInexistente = "id-inexistente";

        ClienteException exception = assertThrows(ClienteException.class, () -> {
            clienteService.atualizar(idInexistente, commandAtualizar);
        });

        assertEquals("Cliente não encontrado: " + idInexistente, exception.getMessage());
    }

    @Test
    void deveDeletarClienteQuandoClienteExiste() {
        // Criar cliente
        var endereco = new EnderecoCommand("55555555", "Rua Deletar", "5", null, "Bairro Deletar", "Cidade Deletar", "DD");
        var command = new CriarClienteCommand("55555555555", "Cliente Para Deletar", "5555-5555", endereco);
        Cliente clienteCriado = clienteService.criar(command);

        // Verificar que existe
        Optional<Cliente> clienteAntes = clienteRepository.findById(clienteCriado.getId());
        assertTrue(clienteAntes.isPresent());

        // Deletar
        clienteService.deletar(clienteCriado.getId());

        // Verificar que não existe mais
        Optional<Cliente> clienteDepois = clienteRepository.findById(clienteCriado.getId());
        assertFalse(clienteDepois.isPresent());
    }

    @Test
    void deveLancarExcecaoQuandoDeletarClienteInexistente() {
        String idInexistente = "id-para-deletar-inexistente";

        ClienteException exception = assertThrows(ClienteException.class, () -> {
            clienteService.deletar(idInexistente);
        });

        assertEquals("Cliente não encontrado: " + idInexistente, exception.getMessage());
    }
}
