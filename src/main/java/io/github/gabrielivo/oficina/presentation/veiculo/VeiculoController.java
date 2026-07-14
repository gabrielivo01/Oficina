package io.github.gabrielivo.oficina.presentation.veiculo;

import io.github.gabrielivo.oficina.application.veiculo.*;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/veiculos")
public class VeiculoController {

    private final VeiculoService veiculoService;
    private final VeiculoMapper veiculoMapper;

    public VeiculoController(VeiculoService veiculoService, VeiculoMapper veiculoMapper) {
        this.veiculoService = veiculoService;
        this.veiculoMapper = veiculoMapper;
    }

    @PostMapping
    public ResponseEntity<VeiculoResponse> criar(@Valid @RequestBody VeiculoRequest request) {
        Veiculo veiculo = veiculoService.criar(veiculoMapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(veiculoMapper.toResponse(veiculo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VeiculoResponse> buscarPorId(@PathVariable String id) {
        return ResponseEntity.ok(veiculoMapper.toResponse(veiculoService.buscarPorId(id)));
    }

    @GetMapping
    public ResponseEntity<List<VeiculoResponse>> listarTodos() {
        return ResponseEntity.ok(veiculoService.listarTodos().stream()
            .map(veiculoMapper::toResponse)
            .toList());
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<VeiculoResponse>> listarPorCliente(@PathVariable String clienteId) {
        return ResponseEntity.ok(veiculoService.listarPorCliente(clienteId).stream()
            .map(veiculoMapper::toResponse)
            .toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<VeiculoResponse> atualizar(
        @PathVariable String id,
        @Valid @RequestBody AtualizarVeiculoRequest request
    ) {
        Veiculo veiculo = veiculoService.atualizar(id, veiculoMapper.toCommand(request));
        return ResponseEntity.ok(veiculoMapper.toResponse(veiculo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        veiculoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}