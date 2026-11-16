package io.github.gabrielivo.oficina.application.mensageria;

/**
 * Porta para o contexto de trace atual. O outbox guarda o {@code traceparent} de quem gravou a mensagem para que a
 * publicação (feita depois, em outra thread) continue o mesmo trace (W3C Trace Context).
 */
public interface ContextoRastreamento {

    /** {@code traceparent} W3C do span corrente, ou {@code null} se não houver trace ativo. */
    String traceparentAtual();
}
