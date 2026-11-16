package io.github.gabrielivo.oficina.integracao;

import io.github.gabrielivo.oficina.application.ordemServico.StatusNotificationPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base dos testes de integração: aplicação completa contra PostgreSQL e RabbitMQ reais (Testcontainers). Todas as
 * subclasses compartilham o mesmo contexto (e os mesmos containers) graças ao cache de contexto do Spring.
 */
@SpringBootTest
@Import({PostgresContainerConfig.class, RabbitMqContainerConfig.class})
abstract class IntegracaoTestBase {

    /** Efeito externo (e-mail) observado nos testes; o resto do fluxo é real. */
    @MockitoBean
    protected StatusNotificationPort statusNotificationPort;
}
