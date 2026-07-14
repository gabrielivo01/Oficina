package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;

public record OrdemServicoStatusResponse(
    String id,
    StatusOrdemServico status
) {}
