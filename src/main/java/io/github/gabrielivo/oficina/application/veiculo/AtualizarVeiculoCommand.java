package io.github.gabrielivo.oficina.application.veiculo;

public record AtualizarVeiculoCommand(
    String marca,
    String modelo,
    Integer ano
) {}