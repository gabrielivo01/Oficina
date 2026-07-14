package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.application.ordemServico.AbrirOrdemServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import io.github.gabrielivo.oficina.presentation.ordemServico.OrdemServicoStatusResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdemServicoControllerTest {

    @Mock
    private OrdemServicoService ordemServicoService;

    @Mock
    private OrdemServicoMapper ordemServicoMapper;

    @InjectMocks
    private OrdemServicoController ordemServicoController;

    @Test
    void deveAbrirOrdemServicoERetornarCreated() {
        OrdemServicoRequest request = new OrdemServicoRequest("cliente-id", "veiculo-id", null);
        AbrirOrdemServicoCommand command = new AbrirOrdemServicoCommand("cliente-id", "veiculo-id");
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico ordemServico = new OrdemServico(cliente, veiculo);
        OrdemServicoResponse responseBody = new OrdemServicoResponse(ordemServico.getId(), "cliente-id", "Gabriel", "veiculo-id", "ABC1234", StatusOrdemServico.RECEBIDA, BigDecimal.ZERO, List.of(), ordemServico.getCriadoEm(), ordemServico.getAtualizadoEm());

        when(ordemServicoMapper.toCommand(request)).thenReturn(command);
        when(ordemServicoService.abrir(command)).thenReturn(ordemServico);
        when(ordemServicoMapper.toResponse(ordemServico)).thenReturn(responseBody);

        ResponseEntity<OrdemServicoResponse> response = ordemServicoController.abrir(request);

        assertEquals(201, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveBuscarOrdemServicoPorIdERetornarOk() {
        String id = "os-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico ordemServico = new OrdemServico(cliente, veiculo);
        OrdemServicoResponse responseBody = new OrdemServicoResponse(ordemServico.getId(), "cliente-id", "Gabriel", "veiculo-id", "ABC1234", StatusOrdemServico.RECEBIDA, BigDecimal.ZERO, List.of(), ordemServico.getCriadoEm(), ordemServico.getAtualizadoEm());

        when(ordemServicoService.buscarPorId(id)).thenReturn(ordemServico);
        when(ordemServicoMapper.toResponse(ordemServico)).thenReturn(responseBody);

        ResponseEntity<OrdemServicoResponse> response = ordemServicoController.buscarPorId(id);

        assertEquals(200, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveAvancarStatusERetornarOk() {
        String id = "os-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico ordemServico = new OrdemServico(cliente, veiculo);
        OrdemServicoResponse responseBody = new OrdemServicoResponse(ordemServico.getId(), "cliente-id", "Gabriel", "veiculo-id", "ABC1234", StatusOrdemServico.EM_EXECUCAO, BigDecimal.ZERO, List.of(), ordemServico.getCriadoEm(), ordemServico.getAtualizadoEm());

        when(ordemServicoService.avancarStatus(id)).thenReturn(ordemServico);
        when(ordemServicoMapper.toResponse(ordemServico)).thenReturn(responseBody);

        ResponseEntity<OrdemServicoResponse> response = ordemServicoController.avancarStatus(id);

        assertEquals(200, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
        verify(ordemServicoService).avancarStatus(id);
    }

    @Test
    void deveRetornarStatusAtualDaOrdemServico() {
        String id = "os-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico ordemServico = new OrdemServico(cliente, veiculo);
        OrdemServicoStatusResponse responseBody = new OrdemServicoStatusResponse(ordemServico.getId(), StatusOrdemServico.RECEBIDA);

        when(ordemServicoService.buscarPorId(id)).thenReturn(ordemServico);

        ResponseEntity<OrdemServicoStatusResponse> response = ordemServicoController.consultarStatus(id);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(responseBody.status(), response.getBody().status());
    }
}
