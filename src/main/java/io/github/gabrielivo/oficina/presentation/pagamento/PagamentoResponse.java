package io.github.gabrielivo.oficina.presentation.pagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;

public record PagamentoResponse(
    String id,
    String ordemServicoId,
    BigDecimal valor,
    FormaPagamento formaPagamento,
    LocalDateTime criadoEm
) {}
