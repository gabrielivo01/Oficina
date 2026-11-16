package io.github.gabrielivo.oficina.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Com {@code app.security.enabled=false} (perfil {@code local}), a cadeia de segurança libera todas as rotas: um
 * endpoint que em produção exige JWT responde sem token.
 */
@SpringBootTest(
        properties = {
            "app.security.enabled=false",
            // Contexto H2 sem broker: não consome nem publica
            "spring.rabbitmq.listener.simple.auto-startup=false",
            "app.mensageria.outbox.publicador-habilitado=false"
        })
@AutoConfigureMockMvc
class SegurancaDesligadaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointProtegidoDeveResponderSemTokenComSegurancaDesligada() throws Exception {
        mockMvc.perform(get("/ordens-servico")).andExpect(status().isOk());
    }
}
