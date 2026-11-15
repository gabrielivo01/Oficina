package io.github.gabrielivo.oficina.presentation.pagamento;

import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagamentoResponse(
        String id, String ordemServicoId, BigDecimal valor, FormaPagamento formaPagamento, LocalDateTime criadoEm) {}
