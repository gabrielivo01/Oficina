package io.github.gabrielivo.oficina.presentation.cliente;

import io.github.gabrielivo.oficina.application.cliente.AtualizarClienteCommand;
import io.github.gabrielivo.oficina.application.cliente.ClienteService;
import io.github.gabrielivo.oficina.application.cliente.CriarClienteCommand;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.Endereco;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteControllerTest {

    @Mock
    private ClienteService clienteService;

    @Mock
    private ClienteMapper clienteMapper;

    @InjectMocks
    private ClienteController clienteController;

    @Test
    void deveCriarClienteERetornarResponseCreated() {
        EnderecoRequest enderecoRequest = new EnderecoRequest("12345678", "Rua Teste", "100", "Apto 1", "Centro", "Cidade", "SP");
        ClienteRequest request = new ClienteRequest("11122233344", "Gabriel", "61999999999", enderecoRequest);
        CriarClienteCommand command = new CriarClienteCommand("11122233344", "Gabriel", "61999999999", new io.github.gabrielivo.oficina.application.cliente.EnderecoCommand("12345678", "Rua Teste", "100", "Apto 1", "Centro", "Cidade", "SP"));
        Endereco endereco = new Endereco("12345678", "Rua Teste", "100", "Apto 1", "Centro", "Cidade", "SP");
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", endereco);
        ClienteResponse responseBody = new ClienteResponse(cliente.getId(), cliente.getCpf(), cliente.getNome(), cliente.getTelefone(), new EnderecoResponse(null, endereco.getCep(), endereco.getLogradouro(), endereco.getNumero(), endereco.getComplemento(), endereco.getBairro(), endereco.getCidade(), endereco.getUf()), cliente.getCriadoEm(), cliente.getAtualizadoEm());

        when(clienteMapper.toCommand(request)).thenReturn(command);
        when(clienteService.criar(command)).thenReturn(cliente);
        when(clienteMapper.toResponse(cliente)).thenReturn(responseBody);

        ResponseEntity<ClienteResponse> response = clienteController.criar(request);

        assertEquals(201, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveBuscarClientePorIdERetornarOk() {
        String id = "cliente-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        ClienteResponse responseBody = new ClienteResponse(id, cliente.getCpf(), cliente.getNome(), cliente.getTelefone(), null, cliente.getCriadoEm(), cliente.getAtualizadoEm());

        when(clienteService.buscarPorId(id)).thenReturn(cliente);
        when(clienteMapper.toResponse(cliente)).thenReturn(responseBody);

        ResponseEntity<ClienteResponse> response = clienteController.buscarPorId(id);

        assertEquals(200, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveListarTodosOsClientesERetornarOk() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        ClienteResponse responseBody = new ClienteResponse(cliente.getId(), cliente.getCpf(), cliente.getNome(), cliente.getTelefone(), null, cliente.getCriadoEm(), cliente.getAtualizadoEm());

        when(clienteService.listarTodos()).thenReturn(List.of(cliente));
        when(clienteMapper.toResponse(cliente)).thenReturn(responseBody);

        ResponseEntity<List<ClienteResponse>> response = clienteController.listarTodos();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertSame(responseBody, response.getBody().get(0));
    }

    @Test
    void deveAtualizarClienteERetornarOk() {
        String id = "cliente-id";
        EnderecoRequest enderecoRequest = new EnderecoRequest("87654321", "Rua Atualizada", "200", "Casa", "Bairro Novo", "Cidade Nova", "RJ");
        AtualizarClienteRequest request = new AtualizarClienteRequest("Gabriel Atualizado", "61999999999", enderecoRequest);
        AtualizarClienteCommand command = new AtualizarClienteCommand("Gabriel Atualizado", "61999999999", new io.github.gabrielivo.oficina.application.cliente.EnderecoCommand("87654321", "Rua Atualizada", "200", "Casa", "Bairro Novo", "Cidade Nova", "RJ"));
        Endereco endereco = new Endereco("87654321", "Rua Atualizada", "200", "Casa", "Bairro Novo", "Cidade Nova", "RJ");
        Cliente cliente = new Cliente("11122233344", "Gabriel Atualizado", "61999999999", endereco);
        ClienteResponse responseBody = new ClienteResponse(id, cliente.getCpf(), cliente.getNome(), cliente.getTelefone(), new EnderecoResponse(null, endereco.getCep(), endereco.getLogradouro(), endereco.getNumero(), endereco.getComplemento(), endereco.getBairro(), endereco.getCidade(), endereco.getUf()), cliente.getCriadoEm(), cliente.getAtualizadoEm());

        when(clienteMapper.toCommand(request)).thenReturn(command);
        when(clienteService.atualizar(id, command)).thenReturn(cliente);
        when(clienteMapper.toResponse(cliente)).thenReturn(responseBody);

        ResponseEntity<ClienteResponse> response = clienteController.atualizar(id, request);

        assertEquals(200, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveDeletarClienteERetornarNoContent() {
        String id = "cliente-id";

        ResponseEntity<Void> response = clienteController.deletar(id);

        assertEquals(204, response.getStatusCode().value());
        verify(clienteService).deletar(id);
    }
}
