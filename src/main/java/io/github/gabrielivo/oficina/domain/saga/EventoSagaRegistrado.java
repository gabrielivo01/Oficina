package io.github.gabrielivo.oficina.domain.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

@Embeddable
public class EventoSagaRegistrado {

    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    @Column(nullable = false)
    private boolean aplicado;

    @Column(length = 255)
    private String detalhe;

    protected EventoSagaRegistrado() {}

    EventoSagaRegistrado(String messageId, String tipo, Instant ocorridoEm, boolean aplicado, String detalhe) {
        this.messageId = messageId;
        this.tipo = tipo;
        this.ocorridoEm = ocorridoEm;
        this.aplicado = aplicado;
        this.detalhe = detalhe;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public boolean isAplicado() {
        return aplicado;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
