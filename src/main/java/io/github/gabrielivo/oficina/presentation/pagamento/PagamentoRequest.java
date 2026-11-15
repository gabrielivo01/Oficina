package io.github.gabrielivo.oficina.presentation.pagamento;

import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PagamentoRequest(
        @NotNull(message = "Valor é obrigatório") @Positive(message = "Valor deve ser maior que zero")
        BigDecimal valor,

        @NotNull(message = "Forma de pagamento é obrigatória")
        FormaPagamento formaPagamento) {}
