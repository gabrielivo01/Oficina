package io.github.gabrielivo.oficina.application.mensageria;

import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutbox;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Publica os pendentes do outbox. Sem transação própria: cada mensagem é publicada fora de transação e só depois do ack
 * é marcada como enviada (transação curta em {@link OutboxService}). Na primeira falha o ciclo para, para não publicar
 * mensagens mais novas antes de uma mais antiga; ela é tentada de novo no próximo ciclo.
 *
 * <p>Com mais de uma réplica, a mesma mensagem pode ser publicada duas vezes (entrega "pelo menos uma vez"); os
 * consumidores são idempotentes pelo inbox.
 */
@Service
public class PublicacaoOutboxService {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoOutboxService.class);

    private final OutboxService outboxService;
    private final PublicadorMensagens publicador;
    private final MensageriaProperties properties;
    private final Counter publicadas;
    private final Counter falhas;
    private final AtomicLong pendentes = new AtomicLong();

    public PublicacaoOutboxService(
            OutboxService outboxService,
            PublicadorMensagens publicador,
            MensageriaProperties properties,
            MeterRegistry meterRegistry) {
        this.outboxService = outboxService;
        this.publicador = publicador;
        this.properties = properties;
        this.publicadas = meterRegistry.counter("oficina.outbox.publicadas");
        this.falhas = meterRegistry.counter("oficina.outbox.falhas");
        meterRegistry.gauge("oficina.outbox.pendentes", pendentes);
    }

    /** @return quantas mensagens foram confirmadas pelo broker neste ciclo */
    public int publicarPendentes() {
        List<MensagemOutbox> lote =
                outboxService.buscarPendentes(properties.outbox().lote());
        int enviadas = 0;
        for (MensagemOutbox mensagem : lote) {
            try {
                publicador.publicar(new MensagemPublicavel(
                        mensagem.getId(),
                        mensagem.getTipo(),
                        mensagem.getExchange(),
                        mensagem.getRoutingKey(),
                        mensagem.getPayload()));
            } catch (RuntimeException e) {
                falhas.increment();
                outboxService.registrarFalha(mensagem.getId(), e.getMessage());
                log.warn(
                        "Falha ao publicar a mensagem {} ({}) do outbox; nova tentativa no próximo ciclo: {}",
                        mensagem.getId(),
                        mensagem.getTipo(),
                        e.getMessage());
                break;
            }
            outboxService.marcarEnviada(mensagem.getId());
            publicadas.increment();
            enviadas++;
        }
        pendentes.set(outboxService.contarPendentes());
        return enviadas;
    }
}
