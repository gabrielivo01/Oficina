package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServico;
import org.springframework.stereotype.Component;

@Component
public class SagaMapper {

    public SagaResponse toResponse(SagaOrdemServico saga) {
        return new SagaResponse(
                saga.getOsId(),
                saga.getEtapaAtual(),
                saga.getSituacao(),
                saga.getMotivoCompensacao(),
                saga.getPrazoExpiracao(),
                saga.getTentativas(),
                saga.getFalhaCompensacao(),
                saga.getCriadoEm(),
                saga.getAtualizadoEm(),
                saga.getHistorico().stream()
                        .map(evento -> new SagaEventoResponse(
                                evento.getMessageId(),
                                evento.getTipo(),
                                evento.getOcorridoEm(),
                                evento.isAplicado(),
                                evento.getDetalhe()))
                        .toList());
    }
}
