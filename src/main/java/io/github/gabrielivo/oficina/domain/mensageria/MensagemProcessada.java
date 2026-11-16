package io.github.gabrielivo.oficina.domain.mensageria;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Persistable;

/**
 * Registro do inbox: o {@code messageId} de cada mensagem já processada (chave primária = idempotência). Gravado na
 * mesma transação do processamento; uma duplicata encontra o registro e é ignorada.
 */
@Entity
@Table(name = "inbox")
public class MensagemProcessada implements Persistable<String> {

    public static final int TAMANHO_MAXIMO_ID = 64;

    /** Sempre novo até ser gravado/lido: {@code save} faz INSERT (duplicata viola a chave) em vez de merge. */
    @Transient
    private boolean novo = true;

    @Id
    @Column(name = "message_id", length = TAMANHO_MAXIMO_ID)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(name = "processado_em", nullable = false)
    private LocalDateTime processadoEm;

    protected MensagemProcessada() {}

    public MensagemProcessada(String messageId, String tipo) {
        if (messageId == null || messageId.isBlank() || messageId.length() > TAMANHO_MAXIMO_ID) {
            throw new IllegalArgumentException("messageId obrigatório, com até " + TAMANHO_MAXIMO_ID + " caracteres.");
        }
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("Tipo da mensagem é obrigatório.");
        }
        this.messageId = messageId;
        this.tipo = tipo;
        this.processadoEm = LocalDateTime.now();
    }

    @PostPersist
    @PostLoad
    void marcarPersistida() {
        this.novo = false;
    }

    @Override
    public String getId() {
        return messageId;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTipo() {
        return tipo;
    }

    public LocalDateTime getProcessadoEm() {
        return processadoEm;
    }
}
