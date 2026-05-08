package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.domain.peca.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PecaService {

    private final PecaRepository pecaRepository;

    public PecaService(PecaRepository pecaRepository) {
        this.pecaRepository = pecaRepository;
    }

    @Transactional
    public Peca criar(CriarPecaCommand command) {
        Peca peca = new Peca(command.nome(), command.descricao(), command.preco(), command.quantidadeEstoque(), command.estoqueMinimo());
        return pecaRepository.save(peca);
    }

    @Transactional(readOnly = true)
    public Peca buscarPorId(String id) {
        return pecaRepository.findById(id)
            .orElseThrow(() -> new PecaException("Peça não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<Peca> listarTodas() {
        return pecaRepository.findAll();
    }

    @Transactional
    public Peca atualizar(String id, AtualizarPecaCommand command) {
        Peca peca = buscarPorId(id);
        peca.atualizar(command.nome(), command.descricao(), command.preco(), command.quantidadeEstoque(), command.estoqueMinimo());
        return pecaRepository.save(peca);
    }

    @Transactional
    public void deletar(String id) {
        Peca peca = buscarPorId(id);
        pecaRepository.delete(peca);
    }

    @Transactional
    public Peca reporEstoque(String id, int quantidade) {
        Peca peca = buscarPorId(id);
        peca.reporEstoque(quantidade);
        return pecaRepository.save(peca);
    }

    @Transactional(readOnly = true)
    public List<Peca> listarPecasComEstoqueBaixo() {
        return pecaRepository.findAll().stream()
            .filter(Peca::estoqueEstaBaixo)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Peca> listarPecasComEstoqueCritico() {
        return pecaRepository.findAll().stream()
            .filter(Peca::estoqueEstaCritico)
            .toList();
    }
}