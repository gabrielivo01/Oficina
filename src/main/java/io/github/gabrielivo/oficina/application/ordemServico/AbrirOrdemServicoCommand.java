package io.github.gabrielivo.oficina.application.ordemServico;

public record AbrirOrdemServicoCommand(
    String clienteId,
    String veiculoId
) {}