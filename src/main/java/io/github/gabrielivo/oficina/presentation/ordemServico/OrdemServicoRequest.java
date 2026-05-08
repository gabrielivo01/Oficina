package io.github.gabrielivo.oficina.presentation.ordemServico;

import jakarta.validation.constraints.NotBlank;

public record OrdemServicoRequest(
    @NotBlank(message = "clienteId é obrigatório")
    String clienteId,

    @NotBlank(message = "veiculoId é obrigatório")
    String veiculoId
) {}