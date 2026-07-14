package io.github.gabrielivo.oficina.presentation.ordemServico;



import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/ordens-servico")
@Tag(name = "Ordens de Serviço", description = "Fluxo de abertura, acompanhamento, aprovação e atualização de ordens de serviço")
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;
    private final OrdemServicoMapper ordemServicoMapper;

    public OrdemServicoController(OrdemServicoService ordemServicoService, OrdemServicoMapper ordemServicoMapper) {
        this.ordemServicoService = ordemServicoService;
        this.ordemServicoMapper = ordemServicoMapper;
    }

    @Operation(
        summary = "Abrir ordem de serviço",
        description = "Cria uma nova OS recebendo cliente, veículo, serviços e peças",
        responses = {
            @ApiResponse(responseCode = "201", description = "OS criada com sucesso", content = @Content(schema = @Schema(implementation = OrdemServicoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido")
        }
    )
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

    @Operation(
        summary = "Consultar status da OS",
        description = "Retorna o status atual da ordem de serviço",
        responses = {
            @ApiResponse(responseCode = "200", description = "Status retornado com sucesso", content = @Content(schema = @Schema(implementation = OrdemServicoStatusResponse.class)))
        }
    )
    @GetMapping("/{id}/status")
    public ResponseEntity<OrdemServicoStatusResponse> consultarStatus(@PathVariable String id) {
        OrdemServico os = ordemServicoService.buscarPorId(id);
        return ResponseEntity.ok(new OrdemServicoStatusResponse(os.getId(), os.getStatus()));
    }

    @Operation(
        summary = "Responder aprovação de orçamento",
        description = "Recebe a decisão do cliente sobre o orçamento e atualiza o status da OS",
        responses = {
            @ApiResponse(responseCode = "200", description = "Resposta registrada com sucesso", content = @Content(schema = @Schema(implementation = OrdemServicoResponse.class)))
        }
    )
    @PostMapping("/{id}/aprovacao-orcamento")
    public ResponseEntity<OrdemServicoResponse> responderOrcamento(
        @Parameter(description = "Identificador da ordem de serviço") @PathVariable String id,
        @Valid @RequestBody ResponderOrcamentoRequest request
    ) {
        OrdemServico os = ordemServicoService.responderOrcamento(id, request.aprovado(), request.observacao());
        return ResponseEntity.ok(ordemServicoMapper.toResponse(os));
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