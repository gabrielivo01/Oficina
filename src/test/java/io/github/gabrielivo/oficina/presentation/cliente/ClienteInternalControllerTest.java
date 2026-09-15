package io.github.gabrielivo.oficina.presentation.cliente;

import io.github.gabrielivo.oficina.application.cliente.ClienteService;
import io.github.gabrielivo.oficina.application.cliente.ClienteStatusConsulta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteInternalControllerTest {

    @Mock
    private ClienteService clienteService;

    @Mock
    private ClienteMapper clienteMapper;

    @InjectMocks
    private ClienteInternalController clienteInternalController;

    @Test
    void deveRetornarStatusQuandoClienteExisteEAtivo() {
        String cpf = "11122233344";
        ClienteStatusConsulta consulta = new ClienteStatusConsulta(true, true);
        ClienteStatusResponse responseBody = new ClienteStatusResponse(true, true);

        when(clienteService.consultarStatusPorCpf(cpf)).thenReturn(consulta);
        when(clienteMapper.toStatusResponse(consulta)).thenReturn(responseBody);

        ResponseEntity<ClienteStatusResponse> response = clienteInternalController.consultarStatusPorCpf(cpf);

        assertEquals(200, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveRetornarStatusInexistenteQuandoCpfNaoCadastrado() {
        String cpf = "00000000000";
        ClienteStatusConsulta consulta = new ClienteStatusConsulta(false, false);
        ClienteStatusResponse responseBody = new ClienteStatusResponse(false, false);

        when(clienteService.consultarStatusPorCpf(cpf)).thenReturn(consulta);
        when(clienteMapper.toStatusResponse(consulta)).thenReturn(responseBody);

        ResponseEntity<ClienteStatusResponse> response = clienteInternalController.consultarStatusPorCpf(cpf);

        assertEquals(200, response.getStatusCode().value());
        assertFalse(response.getBody().existe());
    }
}
