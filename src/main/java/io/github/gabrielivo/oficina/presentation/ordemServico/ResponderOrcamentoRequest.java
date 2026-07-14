package io.github.gabrielivo.oficina.presentation.ordemServico;

import jakarta.validation.constraints.NotNull;

public record ResponderOrcamentoRequest(
    @NotNull(message = "A aprovação é obrigatória")
    Boolean aprovado,
    String observacao
) {}
