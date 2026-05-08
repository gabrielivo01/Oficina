package io.github.gabrielivo.oficina.presentation.ordemServico;



import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;

import java.util.List;

@RestController
@RequestMapping("/ordens-servico")
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;
    private final OrdemServicoMapper ordemServicoMapper;

    public OrdemServicoController(OrdemServicoService ordemServicoService, OrdemServicoMapper ordemServicoMapper) {
        this.ordemServicoService = ordemServicoService;
        this.ordemServicoMapper = ordemServicoMapper;
    }

    @PostMapping
    public ResponseEntity<OrdemServicoResponse> abrir(@Valid @RequestBody OrdemServicoRequest request) {
        OrdemServico os = ordemServicoService.abrir(ordemServicoMapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ordemServicoMapper.toResponse(os));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable String id) {
        return ResponseEntity.ok(ordemServicoMapper.toResponse(ordemServicoService.buscarPorId(id)));
    }

    @GetMapping
    public ResponseEntity<List<OrdemServicoResponse>> listarTodas() {
        List<OrdemServicoResponse> lista = ordemServicoService.listarTodas()
            .stream().map(ordemServicoMapper::toResponse).toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<OrdemServicoResponse>> listarPorCliente(@PathVariable String clienteId) {
        List<OrdemServicoResponse> lista = ordemServicoService.listarPorCliente(clienteId)
            .stream().map(ordemServicoMapper::toResponse).toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/veiculo/{veiculoId}")
    public ResponseEntity<List<OrdemServicoResponse>> listarPorVeiculo(@PathVariable String veiculoId) {
        List<OrdemServicoResponse> lista = ordemServicoService.listarPorVeiculo(veiculoId)
            .stream().map(ordemServicoMapper::toResponse).toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<OrdemServicoResponse> adicionarItem(
        @PathVariable String id,
        @Valid @RequestBody AdicionarItemOSRequest request
    ) {
        OrdemServico os = ordemServicoService.adicionarItem(ordemServicoMapper.toCommand(id, request));
        return ResponseEntity.ok(ordemServicoMapper.toResponse(os));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    public ResponseEntity<OrdemServicoResponse> removerItem(
        @PathVariable String id,
        @PathVariable String itemId
    ) {
        OrdemServico os = ordemServicoService.removerItem(id, itemId);
        return ResponseEntity.ok(ordemServicoMapper.toResponse(os));
    }

    @PatchMapping("/{id}/avancar-status")
    public ResponseEntity<OrdemServicoResponse> avancarStatus(@PathVariable String id) {
        OrdemServico os = ordemServicoService.avancarStatus(id);
        return ResponseEntity.ok(ordemServicoMapper.toResponse(os));
    }
}