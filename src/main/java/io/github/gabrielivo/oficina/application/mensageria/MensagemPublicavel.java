package io.github.gabrielivo.oficina.application.mensageria;

/**
 * Dados de uma mensagem do outbox prontos para ir ao broker; {@code corpo} é o envelope JSON e {@code traceParent} o
 * contexto de trace de quem a gravou (pode ser {@code null}).
 */
public record MensagemPublicavel(
        String messageId, String tipo, String exchange, String routingKey, String corpo, String traceParent) {}
