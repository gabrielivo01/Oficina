package io.github.gabrielivo.oficina.presentation.ordemServico;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrdemServicoRequest(
    @NotBlank(message = "clienteId é obrigatório")
    String clienteId,

    @NotBlank(message = "veiculoId é obrigatório")
    String veiculoId,

    @NotEmpty(message = "A ordem de serviço deve ter pelo menos um item")
    List<@Valid ItemOrdemServicoRequest> itens
) {}