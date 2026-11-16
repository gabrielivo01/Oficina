package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.domain.saga.EtapaSaga;
import io.github.gabrielivo.oficina.domain.saga.MotivoCompensacao;
import io.github.gabrielivo.oficina.domain.saga.SituacaoSaga;
import java.time.Instant;
import java.util.List;

public record SagaResponse(
        String osId,
        EtapaSaga etapaAtual,
        SituacaoSaga situacao,
        MotivoCompensacao motivoCompensacao,
        Instant prazoExpiracao,
        int tentativas,
        String falhaCompensacao,
        Instant criadoEm,
        Instant atualizadoEm,
        List<SagaEventoResponse> historico) {

    public SagaResponse {
        historico = List.copyOf(historico);
    }
}
