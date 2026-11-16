package io.github.gabrielivo.oficina.domain.saga;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.util.List;

public record DecisaoSaga(boolean aplicada, StatusOrdemServico novoStatus, List<ComandoSaga> comandos) {

    public DecisaoSaga {
        comandos = List.copyOf(comandos);
    }

    public static DecisaoSaga ignorada() {
        return new DecisaoSaga(false, null, List.of());
    }
}
