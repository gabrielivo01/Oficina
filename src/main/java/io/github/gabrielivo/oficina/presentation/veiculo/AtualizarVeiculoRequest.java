package io.github.gabrielivo.oficina.presentation.veiculo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AtualizarVeiculoRequest(
    @NotBlank(message = "Marca é obrigatória")
    String marca,

    @NotBlank(message = "Modelo é obrigatório")
    String modelo,

    @NotNull(message = "Ano é obrigatório")
    Integer ano
) {}