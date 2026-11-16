package io.github.gabrielivo.oficina.domain.mensageria;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Persistable;

/**
 * Mensagem a publicar no RabbitMQ, gravada na mesma transação da mudança de estado (Transactional Outbox, ADR 0009). O
 * publicador agendado a envia e só a marca como enviada após o ack do broker (publisher confirms).
 */
@Entity
@Table(name = "outbox")
public class MensagemOutbox implements Persistable<String> {

    static final int TAMANHO_MAXIMO_ERRO = 500;

    /** Id atribuído pela aplicação: sem isto o {@code save} faria um SELECT (merge) antes de cada INSERT. */
    @Transient
    private boolean novo = true;

    /** Igual ao {@code messageId} do envelope. */
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(nullable = false, length = 100)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    /** Envelope completo em JSON, publicado como está. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = TAMANHO_MAXIMO_ERRO)
    private String ultimoErro;

    /** W3C {@code traceparent} de quem gravou a mensagem; a publicação continua esse trace. */
    @Column(name = "trace_parent", length = 100)
    private String traceParent;

    protected MensagemOutbox() {}

    public MensagemOutbox(String id, String tipo, String exchange, String routingKey, String payload) {
        this(id, tipo, exchange, routingKey, payload, null);
    }

    public MensagemOutbox(
            String id, String tipo, String exchange, String routingKey, String payload, String traceParent) {
        exigirTexto(id, "id");
        exigirTexto(tipo, "tipo");
        exigirTexto(exchange, "exchange");
        exigirTexto(routingKey, "routingKey");
        exigirTexto(payload, "payload");
        this.id = id;
        this.tipo = tipo;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
        this.traceParent = traceParent;
        this.criadoEm = LocalDateTime.now();
        this.tentativas = 0;
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório da mensagem do outbox: " + campo);
        }
    }

    /** Chamado só depois do ack do broker. Idempotente: uma mensagem já enviada mantém a data original. */
    public void marcarEnviada() {
        if (this.enviadoEm == null) {
            this.tentativas++;
            this.enviadoEm = LocalDateTime.now();
            this.ultimoErro = null;
        }
    }

    public void registrarFalha(String erro) {
        this.tentativas++;
        String mensagem = erro == null || erro.isBlank() ? "Falha sem mensagem" : erro;
        this.ultimoErro =
                mensagem.length() > TAMANHO_MAXIMO_ERRO ? mensagem.substring(0, TAMANHO_MAXIMO_ERRO) : mensagem;
    }

    public boolean isEnviada() {
        return enviadoEm != null;
    }

    @PostPersist
    @PostLoad
    void marcarPersistida() {
        this.novo = false;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public String getTraceParent() {
        return traceParent;
    }
}
