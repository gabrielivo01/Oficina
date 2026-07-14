package io.github.gabrielivo.oficina.presentation.ordemServico;

import java.math.BigDecimal;

import io.github.gabrielivo.oficina.domain.ordemServico.TipoItemOrdemServico;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemOrdemServicoRequest(
    @NotBlank(message = "Descrição é obrigatória")
    String descricao,

    @NotNull(message = "Tipo é obrigatório")
    TipoItemOrdemServico tipo,

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    BigDecimal valor,

    @NotNull(message = "Quantidade é obrigatória")
    @Positive(message = "Quantidade deve ser maior que zero")
    Integer quantidade,

    String pecaId
) {}
