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
        CriarVeiculoCommand command = veiculoMapper.toCommand(request);
        Veiculo veiculo = veiculoService.criar(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(veiculoMapper.toResponse(veiculo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VeiculoResponse> buscarPorId(@PathVariable String id) {
        Veiculo veiculo = veiculoService.buscarPorId(id);
        return ResponseEntity.ok(veiculoMapper.toResponse(veiculo));
    }

    @GetMapping
    public ResponseEntity<List<VeiculoResponse>> listarTodos() {
        List<VeiculoResponse> veiculos = veiculoService.listarTodos()
            .stream()
            .map(veiculoMapper::toResponse)
            .toList();
        return ResponseEntity.ok(veiculos);
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<VeiculoResponse>> listarPorCliente(@PathVariable String clienteId) {
        List<VeiculoResponse> veiculos = veiculoService.listarPorCliente(clienteId)
            .stream()
            .map(veiculoMapper::toResponse)
            .toList();
        return ResponseEntity.ok(veiculos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VeiculoResponse> atualizar(
        @PathVariable String id,
        @Valid @RequestBody AtualizarVeiculoRequest request
    ) {
        AtualizarVeiculoCommand command = veiculoMapper.toCommand(request);
        Veiculo veiculo = veiculoService.atualizar(id, command);
        return ResponseEntity.ok(veiculoMapper.toResponse(veiculo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        veiculoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}