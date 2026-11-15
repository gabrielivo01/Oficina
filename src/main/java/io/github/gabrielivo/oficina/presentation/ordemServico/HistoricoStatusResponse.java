package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.time.LocalDateTime;

public record HistoricoStatusResponse(
        int sequencia,
        StatusOrdemServico statusAnterior,
        StatusOrdemServico statusNovo,
        LocalDateTime alteradoEm,
        OrigemMudancaStatus origem,
        String motivo) {}
