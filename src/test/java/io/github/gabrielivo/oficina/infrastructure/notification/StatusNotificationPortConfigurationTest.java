package io.github.gabrielivo.oficina.infrastructure.notification;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatusNotificationPortConfigurationTest {

    @Test
    void deveRegistrarApenasUmImplementadorQuandoEmailEstiverHabilitado() {
        AnnotationConfigApplicationContext context = criarContextoComPropriedade("true");

        String[] beans = context.getBeanNamesForType(StatusNotificationPort.class);

        assertEquals(1, beans.length);
        assertNotNull(context.getBean(EmailStatusNotificationPort.class));

        context.close();
    }

    @Test
    void deveRegistrarNoopQuandoEmailEstiverDesabilitado() {
        AnnotationConfigApplicationContext context = criarContextoComPropriedade("false");

        String[] beans = context.getBeanNamesForType(StatusNotificationPort.class);

        assertEquals(1, beans.length);
        assertNotNull(context.getBean(NoopStatusNotificationPort.class));

        context.close();
    }

    private AnnotationConfigApplicationContext criarContextoComPropriedade(String valor) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test", Map.of("app.mail.enabled", valor)));
        context.setEnvironment(environment);
        context.registerBean(JavaMailSenderImpl.class, JavaMailSenderImpl::new);
        context.register(EmailStatusNotificationPort.class, NoopStatusNotificationPort.class);
        context.refresh();
        return context;
    }
}
