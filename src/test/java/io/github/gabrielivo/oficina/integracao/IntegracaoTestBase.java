package io.github.gabrielivo.oficina.integracao;

import io.github.gabrielivo.oficina.application.ordemServico.StatusNotificationPort;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base dos testes de integração: aplicação completa (segurança ligada, como em produção) contra PostgreSQL e RabbitMQ
 * reais (Testcontainers). Todas as subclasses compartilham o mesmo contexto (e os mesmos containers) graças ao cache de
 * contexto do Spring. O tracing fica ativo ({@code @AutoConfigureTracing}: sem exportação habilitada o Boot 4 usa
 * propagação no-op); só o exportador OTLP é desligado (não há collector nos testes).
 */
@SpringBootTest(properties = "management.tracing.export.otlp.enabled=false")
@AutoConfigureTracing
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RabbitMqContainerConfig.class})
abstract class IntegracaoTestBase {

    /** Efeito externo (e-mail) observado nos testes; o resto do fluxo é real. */
    @MockitoBean
    protected StatusNotificationPort statusNotificationPort;
}
