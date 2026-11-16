package io.github.gabrielivo.oficina.application.mensageria;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nomes da topologia RabbitMQ usada pelo OS Service e parâmetros do publicador do outbox. A topologia em si (exchanges,
 * filas quorum, DLQs, bindings) é declarada só em {@code oficina-infra-k8s/local/rabbitmq/definitions.json}: o serviço
 * não declara filas (argumento diferente quebraria o boot).
 *
 * @param exchangeEventos exchange topic onde o OS Service publica eventos ({@code oficina.eventos})
 * @param exchangeComandos exchange direct dos comandos da Saga ({@code oficina.comandos}, usado a partir da Fase 5)
 * @param filaEventos fila consumida pelo OS Service ({@code os.saga.eventos}, binding {@code #} em oficina.eventos)
 */
@ConfigurationProperties(prefix = "app.mensageria")
public record MensageriaProperties(
        @DefaultValue("oficina.eventos") String exchangeEventos,
        @DefaultValue("oficina.comandos") String exchangeComandos,
        @DefaultValue("os.saga.eventos") String filaEventos,
        @DefaultValue Outbox outbox) {

    /**
     * @param publicadorHabilitado liga o job que publica os pendentes
     * @param intervalo pausa entre ciclos do publicador
     * @param lote máximo de mensagens por ciclo
     * @param timeoutConfirmacao espera máxima pelo publisher confirm de cada mensagem
     */
    public record Outbox(
            @DefaultValue("true") boolean publicadorHabilitado,
            @DefaultValue("1s") Duration intervalo,
            @DefaultValue("50") int lote,
            @DefaultValue("5s") Duration timeoutConfirmacao) {}
}
