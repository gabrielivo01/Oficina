package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.application.ordemServico.ListarOrdensServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.presentation.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ordens-servico")
@Tag(
        name = "Ordens de Serviço",
        description = "Fluxo de abertura, acompanhamento, aprovação e atualização de ordens de serviço")
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;
    private final OrdemServicoMapper ordemServicoMapper;
    private final SagaMapper sagaMapper;

    public OrdemServicoController(
            OrdemServicoService ordemServicoService, OrdemServicoMapper ordemServicoMapper, SagaMapper sagaMapper) {
        this.ordemServicoService = ordemServicoService;
        this.ordemServicoMapper = ordemServicoMapper;
        this.sagaMapper = sagaMapper;
    }

    @Operation(
            summary = "Abrir ordem de serviço",
            description = "Cria uma nova OS para diagnóstico; orçamento e itens são recebidos pelo fluxo da Saga",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "OS criada com sucesso",
                        content = @Content(schema = @Schema(implementation = OrdemServicoResponse.class))),
                @ApiResponse(responseCode = "400", description = "Payload inválido")
            })
    @PostMapping
    public ResponseEntity<OrdemServicoResponse> abrir(@Valid @RequestBody OrdemServicoRequest request) {
        OrdemServico os = ordemServicoService.abrir(ordemServicoMapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ordemServicoMapper.toResponse(os));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable String id) {
        return ResponseEntity.ok(ordemServicoMapper.toResponse(ordemServicoService.buscarPorId(id)));
    }

    @Operation(
            summary = "Listar ordens de serviço",
            description = "Lista paginada, ordenada por prioridade de atendimento (EM_EXECUCAO, AGUARDANDO_PAGAMENTO,"
                    + " AGUARDANDO_APROVACAO, EM_DIAGNOSTICO, RECEBIDA) e depois pela abertura. Sem 'status',"
                    + " traz só as OS ativas (exclui FINALIZADA, ENTREGUE e CANCELADA).")
    @GetMapping
    public ResponseEntity<PaginaResponse<OrdemServicoResponse>> listar(
            @Parameter(description = "Filtro de status (pode repetir: ?status=RECEBIDA&status=EM_DIAGNOSTICO)")
                    @RequestParam(required = false)
                    Set<StatusOrdemServico> status,
            @Parameter(description = "Página, a partir de 0") @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Tamanho da página (1 a 100)")
                    @RequestParam(defaultValue = "" + ListarOrdensServicoCommand.TAMANHO_PADRAO)
                    int tamanho) {
        Page<OrdemServico> resultado =
                ordemServicoService.listar(new ListarOrdensServicoCommand(status, pagina, tamanho));
        return ResponseEntity.ok(ordemServicoMapper.toResponse(resultado));
    }

    @Operation(
            summary = "Histórico de status da OS",
            description = "Todas as mudanças de status, da abertura à situação atual, com origem e motivo")
    @GetMapping("/{id}/historico")
    public ResponseEntity<List<HistoricoStatusResponse>> listarHistorico(@PathVariable String id) {
        return ResponseEntity.ok(ordemServicoMapper.toHistoricoResponse(ordemServicoService.listarHistorico(id)));
    }

    @Operation(
            summary = "Cancelar ordem de serviço",
            description = "Solicita o cancelamento; a OS só muda para CANCELADA após a conclusão das compensações",
            responses = {
                @ApiResponse(
                        responseCode = "202",
                        description = "Solicitação de cancelamento aceita",
                        content = @Content(schema = @Schema(implementation = OrdemServicoResponse.class))),
                @ApiResponse(responseCode = "422", description = "OS finalizada, entregue ou já cancelada")
            })
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<OrdemServicoResponse> cancelar(
            @PathVariable String id, @Valid @RequestBody(required = false) CancelarOrdemServicoRequest request) {
        OrdemServico os = ordemServicoService.cancelar(id, request == null ? null : request.motivo());
        return ResponseEntity.accepted().body(ordemServicoMapper.toResponse(os));
    }

    @Operation(
            summary = "Consultar status da OS",
            description = "Retorna o status atual da ordem de serviço",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Status retornado com sucesso",
                        content = @Content(schema = @Schema(implementation = OrdemServicoStatusResponse.class)))
            })
    @GetMapping("/{id}/status")
    public ResponseEntity<OrdemServicoStatusResponse> consultarStatus(@PathVariable String id) {
        OrdemServico os = ordemServicoService.buscarPorId(id);
        return ResponseEntity.ok(new OrdemServicoStatusResponse(os.getId(), os.getStatus()));
    }

    @Operation(
            summary = "Consultar estado da Saga",
            description = "Retorna a etapa, situação e histórico de eventos da orquestração da OS")
    @GetMapping("/{id}/saga")
    public ResponseEntity<SagaResponse> consultarSaga(@PathVariable String id) {
        return ResponseEntity.ok(sagaMapper.toResponse(ordemServicoService.consultarSaga(id)));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<OrdemServicoResponse>> listarPorCliente(@PathVariable String clienteId) {
        List<OrdemServicoResponse> lista = ordemServicoService.listarPorCliente(clienteId).stream()
                .map(ordemServicoMapper::toResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/veiculo/{veiculoId}")
    public ResponseEntity<List<OrdemServicoResponse>> listarPorVeiculo(@PathVariable String veiculoId) {
        List<OrdemServicoResponse> lista = ordemServicoService.listarPorVeiculo(veiculoId).stream()
                .map(ordemServicoMapper::toResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping("/{id}/entregar")
    public ResponseEntity<OrdemServicoResponse> entregar(@PathVariable String id) {
        OrdemServico os = ordemServicoService.entregar(id);
        return ResponseEntity.ok(ordemServicoMapper.toResponse(os));
    }
}
