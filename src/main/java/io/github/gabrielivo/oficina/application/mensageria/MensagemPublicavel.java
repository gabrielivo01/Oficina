package io.github.gabrielivo.oficina.application.mensageria;

/** Dados de uma mensagem do outbox prontos para ir ao broker; {@code corpo} é o envelope JSON. */
public record MensagemPublicavel(String messageId, String tipo, String exchange, String routingKey, String corpo) {}
