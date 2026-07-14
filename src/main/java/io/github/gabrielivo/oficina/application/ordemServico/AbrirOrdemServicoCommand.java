package io.github.gabrielivo.oficina.application.ordemServico;

import java.util.List;

public record AbrirOrdemServicoCommand(
    String clienteId,
    String veiculoId,
    List<ItemOrdemServicoCommand> itens
) {
    public AbrirOrdemServicoCommand(String clienteId, String veiculoId) {
        this(clienteId, veiculoId, List.of());
    }
}