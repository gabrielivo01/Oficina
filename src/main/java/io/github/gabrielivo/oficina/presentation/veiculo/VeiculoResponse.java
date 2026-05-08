package io.github.gabrielivo.oficina.presentation.veiculo;

import java.time.LocalDateTime;

public record VeiculoResponse(
    String id,
    String clienteId,
    String nomeCliente,
    String placa,
    String marca,
    String modelo,
    Integer ano,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm
) {}