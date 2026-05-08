package io.github.gabrielivo.oficina.presentation.cliente;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.github.gabrielivo.oficina.application.cliente.AtualizarClienteCommand;
import io.github.gabrielivo.oficina.application.cliente.ClienteService;
import io.github.gabrielivo.oficina.application.cliente.CriarClienteCommand;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;

import java.util.List;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    public ClienteController(ClienteService clienteService, ClienteMapper clienteMapper) {
        this.clienteService = clienteService;
        this.clienteMapper = clienteMapper;
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
        CriarClienteCommand command = clienteMapper.toCommand(request);
        Cliente cliente = clienteService.criar(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteMapper.toResponse(cliente));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable String id) {
        Cliente cliente = clienteService.buscarPorId(id);
        return ResponseEntity.ok(clienteMapper.toResponse(cliente));
    }

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listarTodos() {
        List<ClienteResponse> clientes = clienteService.listarTodos()
            .stream()
            .map(clienteMapper::toResponse)
            .toList();
        return ResponseEntity.ok(clientes);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> atualizar(
        @PathVariable String id,
        @Valid @RequestBody AtualizarClienteRequest request
    ) {
        AtualizarClienteCommand command = clienteMapper.toCommand(request);
        Cliente cliente = clienteService.atualizar(id, command);
        return ResponseEntity.ok(clienteMapper.toResponse(cliente));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        clienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
