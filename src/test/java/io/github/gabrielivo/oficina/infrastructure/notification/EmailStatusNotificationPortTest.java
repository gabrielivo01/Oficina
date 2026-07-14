package io.github.gabrielivo.oficina.infrastructure.notification;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailStatusNotificationPortTest {

    @Test
    void deveEnviarEmailParaDestinatarioConfigurado() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailStatusNotificationPort port = new EmailStatusNotificationPort(mailSender, "from@oficina.local", "cliente@oficina.local");

        port.enviarAtualizacao("OS-001", StatusOrdemServico.RECEBIDA, "Teste de envio");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage mensagem = captor.getValue();
        assertEquals("from@oficina.local", mensagem.getFrom());
        assertArrayEquals(new String[]{"cliente@oficina.local"}, mensagem.getTo());
        assertEquals("Atualização da OS OS-001", mensagem.getSubject());
    }
}
