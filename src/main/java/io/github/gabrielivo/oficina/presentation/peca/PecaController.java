package io.github.gabrielivo.oficina.presentation.peca;

import io.github.gabrielivo.oficina.application.ordemServico.*;
import io.github.gabrielivo.oficina.domain.peca.Peca;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pecas")
public class PecaController {

    private final PecaService pecaService;

    public PecaController(PecaService pecaService) {
        this.pecaService = pecaService;
    }

    @PostMapping
    public ResponseEntity<Peca> criar(@RequestBody CriarPecaRequest request) {
        var command = new CriarPecaCommand(
            request.nome(),
            request.descricao(),
            request.preco(),
            request.quantidadeEstoque(),
            request.estoqueMinimo()
        );
        Peca peca = pecaService.criar(command);
        return ResponseEntity.ok(peca);
    }

    @GetMapping
    public ResponseEntity<List<Peca>> listar() {
        return ResponseEntity.ok(pecaService.listarTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Peca> buscar(@PathVariable String id) {
        return ResponseEntity.ok(pecaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Peca> atualizar(@PathVariable String id, @RequestBody AtualizarPecaRequest request) {
        var command = new AtualizarPecaCommand(
            request.nome(),
            request.descricao(),
            request.preco(),
            request.quantidadeEstoque(),
            request.estoqueMinimo()
        );
        Peca peca = pecaService.atualizar(id, command);
        return ResponseEntity.ok(peca);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        pecaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/repor-estoque")
    public ResponseEntity<Peca> reporEstoque(@PathVariable String id, @RequestParam int quantidade) {
        Peca peca = pecaService.reporEstoque(id, quantidade);
        return ResponseEntity.ok(peca);
    }

    @GetMapping("/alertas/estoque-baixo")
    public ResponseEntity<List<Peca>> alertasEstoqueBaixo() {
        return ResponseEntity.ok(pecaService.listarPecasComEstoqueBaixo());
    }

    @GetMapping("/alertas/estoque-critico")
    public ResponseEntity<List<Peca>> alertasEstoqueCritico() {
        return ResponseEntity.ok(pecaService.listarPecasComEstoqueCritico());
    }
}