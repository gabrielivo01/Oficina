package io.github.gabrielivo.oficina.presentation.veiculo;

import io.github.gabrielivo.oficina.application.veiculo.CriarVeiculoCommand;
import io.github.gabrielivo.oficina.application.veiculo.VeiculoService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoControllerTest {

    @Mock
    private VeiculoService veiculoService;

    @Mock
    private VeiculoMapper veiculoMapper;

    @InjectMocks
    private VeiculoController veiculoController;

    @Test
    void deveCriarVeiculoERetornarCreated() {
        VeiculoRequest request = new VeiculoRequest("cliente-id", "ABC1234", "Fiat", "Uno", 2020);
        CriarVeiculoCommand command = new CriarVeiculoCommand("cliente-id", "ABC1234", "Fiat", "Uno", 2020);
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        VeiculoResponse responseBody = new VeiculoResponse(veiculo.getId(), "cliente-id", "Gabriel", "ABC1234", "Fiat", "Uno", 2020, veiculo.getCriadoEm(), veiculo.getAtualizadoEm());

        when(veiculoMapper.toCommand(request)).thenReturn(command);
        when(veiculoService.criar(command)).thenReturn(veiculo);
        when(veiculoMapper.toResponse(veiculo)).thenReturn(responseBody);

        ResponseEntity<VeiculoResponse> response = veiculoController.criar(request);

        assertEquals(201, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveBuscarVeiculoPorIdERetornarOk() {
        String id = "veiculo-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        VeiculoResponse responseBody = new VeiculoResponse(veiculo.getId(), "cliente-id", "Gabriel", "ABC1234", "Fiat", "Uno", 2020, veiculo.getCriadoEm(), veiculo.getAtualizadoEm());

        when(veiculoService.buscarPorId(id)).thenReturn(veiculo);
        when(veiculoMapper.toResponse(veiculo)).thenReturn(responseBody);

        ResponseEntity<VeiculoResponse> response = veiculoController.buscarPorId(id);

        assertEquals(200, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveListarVeiculosPorClienteERetornarOk() {
        String clienteId = "cliente-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        VeiculoResponse responseBody = new VeiculoResponse(veiculo.getId(), clienteId, "Gabriel", "ABC1234", "Fiat", "Uno", 2020, veiculo.getCriadoEm(), veiculo.getAtualizadoEm());

        when(veiculoService.listarPorCliente(clienteId)).thenReturn(List.of(veiculo));
        when(veiculoMapper.toResponse(veiculo)).thenReturn(responseBody);

        ResponseEntity<List<VeiculoResponse>> response = veiculoController.listarPorCliente(clienteId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertSame(responseBody, response.getBody().get(0));
    }
}
