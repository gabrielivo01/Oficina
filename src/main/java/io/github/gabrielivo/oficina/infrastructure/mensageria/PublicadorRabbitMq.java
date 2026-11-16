package io.github.gabrielivo.oficina.infrastructure.mensageria;

import io.github.gabrielivo.oficina.application.mensageria.FalhaPublicacaoException;
import io.github.gabrielivo.oficina.application.mensageria.MensagemPublicavel;
import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import io.github.gabrielivo.oficina.application.mensageria.PublicadorMensagens;
import io.github.gabrielivo.oficina.infrastructure.observability.ContextoRastreamentoMicrometer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica no RabbitMQ e espera o publisher confirm (requer {@code spring.rabbitmq.publisher-confirm-type=correlated},
 * {@code publisher-returns=true} e {@code template.mandatory=true}). Mensagem persistente, JSON, com {@code messageId}
 * e {@code type} também nas propriedades AMQP.
 */
@Component
public class PublicadorRabbitMq implements PublicadorMensagens {

    private final RabbitTemplate rabbitTemplate;
    private final Duration timeoutConfirmacao;
    private final Tracer tracer;
    private final Propagator propagator;

    public PublicadorRabbitMq(
            RabbitTemplate rabbitTemplate, MensageriaProperties properties, Tracer tracer, Propagator propagator) {
        this.rabbitTemplate = rabbitTemplate;
        this.timeoutConfirmacao = properties.outbox().timeoutConfirmacao();
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /**
     * Publica dentro de um span filho do trace de quem gravou a mensagem no outbox ({@code traceParent}); a observação
     * do RabbitTemplate cria o span de envio sob ele e injeta o {@code traceparent} nos headers AMQP. Assim a
     * requisição HTTP, a publicação e o consumo ficam num único trace, apesar do intervalo do outbox.
     */
    @Override
    public void publicar(MensagemPublicavel mensagem) {
        if (mensagem.traceParent() == null) {
            enviar(mensagem);
            return;
        }
        Span span = propagator
                .extract(Map.of(ContextoRastreamentoMicrometer.TRACEPARENT, mensagem.traceParent()), Map::get)
                .name("outbox publicar " + mensagem.tipo())
                .kind(Span.Kind.PRODUCER)
                .tag("messaging.message.id", mensagem.messageId())
                .tag("messaging.destination.name", mensagem.exchange())
                .start();
        try (Tracer.SpanInScope ignorado = tracer.withSpan(span)) {
            enviar(mensagem);
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private void enviar(MensagemPublicavel mensagem) {
        CorrelationData correlacao = new CorrelationData(mensagem.messageId());
        try {
            rabbitTemplate.send(mensagem.exchange(), mensagem.routingKey(), montar(mensagem), correlacao);
            CorrelationData.Confirm confirmacao =
                    correlacao.getFuture().get(timeoutConfirmacao.toMillis(), TimeUnit.MILLISECONDS);
            ReturnedMessage devolvida = correlacao.getReturned();
            if (devolvida != null) {
                throw new FalhaPublicacaoException("Mensagem não roteada para nenhuma fila (" + devolvida.getReplyText()
                        + ") em " + mensagem.exchange() + "/" + mensagem.routingKey());
            }
            if (!confirmacao.ack()) {
                throw new FalhaPublicacaoException("Broker recusou a mensagem (nack): " + confirmacao.reason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaPublicacaoException("Interrompido aguardando confirmação do broker", e);
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            throw new FalhaPublicacaoException("Sem confirmação do broker: " + e.getMessage(), e);
        }
    }

    private static Message montar(MensagemPublicavel mensagem) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setMessageId(mensagem.messageId());
        propriedades.setType(mensagem.tipo());
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propriedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propriedades.setTimestamp(new Date());
        propriedades.setAppId("oficina-os-service");
        return new Message(mensagem.corpo().getBytes(StandardCharsets.UTF_8), propriedades);
    }
}
