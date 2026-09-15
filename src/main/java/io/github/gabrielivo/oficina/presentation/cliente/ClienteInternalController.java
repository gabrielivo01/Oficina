package io.github.gabrielivo.oficina.presentation.cliente;

import io.github.gabrielivo.oficina.application.cliente.ClienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/clientes")
public class ClienteInternalController {

    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    public ClienteInternalController(ClienteService clienteService, ClienteMapper clienteMapper) {
        this.clienteService = clienteService;
        this.clienteMapper = clienteMapper;
    }

    @GetMapping("/{cpf}/status")
    public ResponseEntity<ClienteStatusResponse> consultarStatusPorCpf(@PathVariable String cpf) {
        return ResponseEntity.ok(clienteMapper.toStatusResponse(clienteService.consultarStatusPorCpf(cpf)));
    }
}
