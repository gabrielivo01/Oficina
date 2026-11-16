package io.github.gabrielivo.oficina.infrastructure.mensageria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.gabrielivo.oficina.application.mensageria.FalhaPublicacaoException;
import io.github.gabrielivo.oficina.application.mensageria.MensagemPublicavel;
import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class PublicadorRabbitMqTest {

    private RabbitTemplate rabbitTemplate;
    private PublicadorRabbitMq publicador;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publicador = new PublicadorRabbitMq(
                rabbitTemplate,
                new MensageriaProperties(
                        "oficina.eventos",
                        "oficina.comandos",
                        "os.saga.eventos",
                        new MensageriaProperties.Outbox(true, Duration.ofSeconds(1), 10, Duration.ofMillis(20))),
                null,
                null);
    }

    @Test
    void publicaMensagemJsonPersistenteEValidaPublisherConfirm() throws Exception {
        doAnswer(invocation -> {
                    CorrelationData correlation = invocation.getArgument(3);
                    Message message = invocation.getArgument(2);
                    assertEquals("message-1", message.getMessageProperties().getMessageId());
                    assertEquals(
                            "PagamentoConfirmado",
                            message.getMessageProperties().getType());
                    assertEquals(
                            MessageDeliveryMode.PERSISTENT,
                            message.getMessageProperties().getDeliveryMode());
                    correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                    return null;
                })
                .when(rabbitTemplate)
                .send(
                        eq("oficina.eventos"),
                        eq("billing.pagamento-confirmado"),
                        any(Message.class),
                        any(CorrelationData.class));

        publicador.publicar(new MensagemPublicavel(
                "message-1", "PagamentoConfirmado", "oficina.eventos", "billing.pagamento-confirmado", "{}", null));

        verify(rabbitTemplate)
                .send(
                        eq("oficina.eventos"),
                        eq("billing.pagamento-confirmado"),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void rejeitaNackDoBroker() throws Exception {
        doAnswer(invocation -> {
                    CorrelationData correlation = invocation.getArgument(3);
                    correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
                    return null;
                })
                .when(rabbitTemplate)
                .send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
        MensagemPublicavel message = new MensagemPublicavel("message-2", "Tipo", "exchange", "key", "{}", null);

        assertThrows(FalhaPublicacaoException.class, () -> publicador.publicar(message));
    }

    @Test
    void rejeitaMensagemDevolvidaMesmoComConfirmAck() throws Exception {
        doAnswer(invocation -> {
                    CorrelationData correlation = invocation.getArgument(3);
                    correlation.setReturned(mock(ReturnedMessage.class));
                    correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                    return null;
                })
                .when(rabbitTemplate)
                .send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThrows(
                FalhaPublicacaoException.class,
                () -> publicador.publicar(new MensagemPublicavel("message-4", "Tipo", "exchange", "key", "{}", null)));
    }

    @Test
    void converteTimeoutDeConfirmacaoEmFalhaDePublicacao() {
        MensagemPublicavel message = new MensagemPublicavel("message-3", "Tipo", "exchange", "key", "{}", null);

        assertThrows(FalhaPublicacaoException.class, () -> publicador.publicar(message));
    }
}
