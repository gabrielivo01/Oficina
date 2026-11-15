package io.github.gabrielivo.oficina.integracao;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL real (mesma versão do Compose/K8s) para testes de integração. O container é um bean: o cache de contexto
 * do Spring reaproveita a mesma instância entre as classes de teste que importam esta configuração.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16").withDatabaseName("oficina_db");
    }
}
