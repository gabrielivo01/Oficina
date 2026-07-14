package io.github.gabrielivo.oficina.application.ordemServico;

public record ResponderOrcamentoCommand(
    boolean aprovado,
    String observacao
) {}
