package io.github.gabrielivo.oficina.presentation.ordemServico;


import io.github.gabrielivo.oficina.domain.ordemServico.TipoItemOrdemServico;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AdicionarItemOSRequest(
    @NotBlank(message = "Descrição é obrigatória")
    String descricao,

    @NotNull(message = "Tipo é obrigatório")
    TipoItemOrdemServico tipo,

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    BigDecimal valor,

    Integer quantidade,
    String pecaId
) {}
