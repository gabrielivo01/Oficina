package io.github.gabrielivo.oficina.presentation.ordemServico;

import jakarta.validation.constraints.Size;

/** Corpo opcional do cancelamento; sem motivo, o histórico registra "Cancelamento solicitado". */
public record CancelarOrdemServicoRequest(@Size(max = 255) String motivo) {}
