package io.github.gabrielivo.oficina.infrastructure.mensageria;

import io.github.gabrielivo.oficina.application.mensageria.PublicacaoOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara a publicação dos pendentes do outbox em intervalo fixo ({@code app.mensageria.outbox.intervalo}). */
@Component
@ConditionalOnProperty(
        name = "app.mensageria.outbox.publicador-habilitado",
        havingValue = "true",
        matchIfMissing = true)
public class AgendadorPublicacaoOutbox {

    private static final Logger log = LoggerFactory.getLogger(AgendadorPublicacaoOutbox.class);

    private final PublicacaoOutboxService publicacaoOutboxService;

    public AgendadorPublicacaoOutbox(PublicacaoOutboxService publicacaoOutboxService) {
        this.publicacaoOutboxService = publicacaoOutboxService;
    }

    @Scheduled(
            fixedDelayString = "${app.mensageria.outbox.intervalo:1s}",
            initialDelayString = "${app.mensageria.outbox.atraso-inicial:5s}")
    public void publicar() {
        try {
            publicacaoOutboxService.publicarPendentes();
        } catch (RuntimeException e) {
            // Ex.: banco indisponível. O próximo ciclo tenta de novo.
            log.error("Ciclo do publicador do outbox falhou: {}", e.getMessage(), e);
        }
    }
}
