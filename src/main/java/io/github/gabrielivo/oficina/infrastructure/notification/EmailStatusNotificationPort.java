package io.github.gabrielivo.oficina.infrastructure.notification;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class EmailStatusNotificationPort implements StatusNotificationPort {

    private final JavaMailSender mailSender;
    private final String from;
    private final String to;
    private final MeterRegistry meterRegistry;

    public EmailStatusNotificationPort(
        JavaMailSender mailSender,
        @Value("${app.mail.from:no-reply@oficina.local}") String from,
        @Value("${app.mail.to:cliente@oficina.local}") String to,
        MeterRegistry meterRegistry
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.to = to;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void enviarAtualizacao(String ordemServicoId, StatusOrdemServico status, String mensagem) {
        SimpleMailMessage email = construirMensagem(ordemServicoId, status, mensagem);
        try {
            mailSender.send(email);
        } catch (MailException e) {
            meterRegistry.counter("oficina.notificacoes.falhas", "canal", "email").increment();
            throw e;
        }
    }

    private SimpleMailMessage construirMensagem(String ordemServicoId, StatusOrdemServico status, String mensagem) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(from);
        email.setTo(to);
        email.setSubject("Atualização da OS " + ordemServicoId);
        email.setText("Status atual: " + status + "\nMensagem: " + mensagem);
        return email;
    }
}
