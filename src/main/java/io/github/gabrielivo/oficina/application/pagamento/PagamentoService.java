package io.github.gabrielivo.oficina.application.pagamento;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gabrielivo.oficina.domain.ordemServico.*;
import io.github.gabrielivo.oficina.domain.pagamento.*;

import java.util.List;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;

    public PagamentoService(
        PagamentoRepository pagamentoRepository,
        OrdemServicoRepository ordemServicoRepository
    ) {
        this.pagamentoRepository = pagamentoRepository;
        this.ordemServicoRepository = ordemServicoRepository;
    }

    @Transactional
    public Pagamento registrar(RegistrarPagamentoCommand command) {
        OrdemServico os = buscarOrdemServico(command.ordemServicoId());
        validarPagamentoDuplicado(command.ordemServicoId());

        Pagamento pagamento = new Pagamento(os, command.valor(), command.formaPagamento());
        return pagamentoRepository.save(pagamento);
    }

    @Transactional(readOnly = true)
    public Pagamento buscarPorId(String id) {
        return pagamentoRepository.findById(id)
            .orElseThrow(() -> new PagamentoException("Pagamento não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Pagamento> listarPorOrdemServico(String ordemServicoId) {
        return pagamentoRepository.findByOrdemServicoId(ordemServicoId);
    }

    private OrdemServico buscarOrdemServico(String ordemServicoId) {
        return ordemServicoRepository.findById(ordemServicoId)
            .orElseThrow(() -> new OrdemServicoException("Ordem de Serviço não encontrada: " + ordemServicoId));
    }

    private void validarPagamentoDuplicado(String ordemServicoId) {
        if (pagamentoRepository.existsByOrdemServicoId(ordemServicoId)) {
            throw new PagamentoException("Esta OS já possui um pagamento registrado.");
        }
    }
}
