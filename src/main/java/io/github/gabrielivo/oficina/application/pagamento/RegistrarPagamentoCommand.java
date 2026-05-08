package io.github.gabrielivo.oficina.application.pagamento;


import java.math.BigDecimal;

import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;

public record RegistrarPagamentoCommand(
    String ordemServicoId,
    BigDecimal valor,
    FormaPagamento formaPagamento
) {}