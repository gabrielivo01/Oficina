package io.github.gabrielivo.oficina.presentation.pagamento;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;

public record PagamentoRequest(
    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    BigDecimal valor,

    @NotNull(message = "Forma de pagamento é obrigatória")
    FormaPagamento formaPagamento
) {}