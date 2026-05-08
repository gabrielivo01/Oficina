package io.github.gabrielivo.oficina.presentation.pagamento;

import org.springframework.stereotype.Component;

import io.github.gabrielivo.oficina.application.pagamento.RegistrarPagamentoCommand;
import io.github.gabrielivo.oficina.domain.pagamento.Pagamento;

@Component
public class PagamentoMapper {

    public RegistrarPagamentoCommand toCommand(String ordemServicoId, PagamentoRequest request) {
        return new RegistrarPagamentoCommand(
            ordemServicoId,
            request.valor(),
            request.formaPagamento()
        );
    }

    public PagamentoResponse toResponse(Pagamento pagamento) {
        return new PagamentoResponse(
            pagamento.getId(),
            pagamento.getOrdemServico().getId(),
            pagamento.getValor(),
            pagamento.getFormaPagamento(),
            pagamento.getCriadoEm()
        );
    }
}