package io.github.gabrielivo.oficina.infrastructure.saga;

import io.github.gabrielivo.oficina.application.ordemServico.OrquestradorSagaService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgendadorSaga {

    private final OrquestradorSagaService orquestrador;

    public AgendadorSaga(OrquestradorSagaService orquestrador) {
        this.orquestrador = orquestrador;
    }

    @Scheduled(fixedDelayString = "${app.saga.intervalo-verificacao:30s}")
    public void processarExpiradas() {
        orquestrador.iniciarDiagnosticosPendentes();
        orquestrador.processarExpiradas();
    }
}
