package io.github.gabrielivo.oficina.application.veiculo;

public record CriarVeiculoCommand(
    String clienteId,
    String placa,
    String marca,
    String modelo,
    Integer ano
) {}
