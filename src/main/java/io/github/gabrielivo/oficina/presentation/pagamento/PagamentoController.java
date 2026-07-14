package io.github.gabrielivo.oficina.presentation.pagamento;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.github.gabrielivo.oficina.application.pagamento.PagamentoService;
import io.github.gabrielivo.oficina.application.pagamento.RegistrarPagamentoCommand;
import io.github.gabrielivo.oficina.domain.pagamento.Pagamento;

import java.util.List;

@RestController
@RequestMapping("/ordens-servico/{ordemServicoId}/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;
    private final PagamentoMapper pagamentoMapper;

    public PagamentoController(PagamentoService pagamentoService, PagamentoMapper pagamentoMapper) {
        this.pagamentoService = pagamentoService;
        this.pagamentoMapper = pagamentoMapper;
    }

    @PostMapping
    public ResponseEntity<PagamentoResponse> registrar(
        @PathVariable String ordemServicoId,
        @Valid @RequestBody PagamentoRequest request
    ) {
        Pagamento pagamento = pagamentoService.registrar(pagamentoMapper.toCommand(ordemServicoId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoMapper.toResponse(pagamento));
    }

    @GetMapping
    public ResponseEntity<List<PagamentoResponse>> listarPorOrdemServico(@PathVariable String ordemServicoId) {
        return ResponseEntity.ok(pagamentoService.listarPorOrdemServico(ordemServicoId).stream()
            .map(pagamentoMapper::toResponse)
            .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagamentoResponse> buscarPorId(@PathVariable String id) {
        return ResponseEntity.ok(pagamentoMapper.toResponse(pagamentoService.buscarPorId(id)));
    }
}
