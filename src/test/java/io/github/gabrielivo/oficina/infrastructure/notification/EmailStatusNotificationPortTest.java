package io.github.gabrielivo.oficina.infrastructure.notification;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailStatusNotificationPortTest {

    @Test
    void deveEnviarEmailParaDestinatarioConfigurado() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EmailStatusNotificationPort port = new EmailStatusNotificationPort(mailSender, "from@oficina.local", "cliente@oficina.local", meterRegistry);

        port.enviarAtualizacao("OS-001", StatusOrdemServico.RECEBIDA, "Teste de envio");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage mensagem = captor.getValue();
        assertEquals("from@oficina.local", mensagem.getFrom());
        assertArrayEquals(new String[]{"cliente@oficina.local"}, mensagem.getTo());
        assertEquals("Atualização da OS OS-001", mensagem.getSubject());
    }

    @Test
    void deveIncrementarContadorDeFalhasERelancarQuandoEnvioFalha() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EmailStatusNotificationPort port = new EmailStatusNotificationPort(mailSender, "from@oficina.local", "cliente@oficina.local", meterRegistry);

        doThrow(new MailSendException("falha simulada")).when(mailSender).send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());

        assertThrows(MailSendException.class,
            () -> port.enviarAtualizacao("OS-002", StatusOrdemServico.RECEBIDA, "Teste de falha"));

        assertEquals(1.0, meterRegistry.counter("oficina.notificacoes.falhas", "canal", "email").count());
    }
}
