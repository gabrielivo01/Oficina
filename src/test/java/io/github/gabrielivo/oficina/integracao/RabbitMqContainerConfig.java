package io.github.gabrielivo.oficina.integracao;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.MountableFile;

/**
 * RabbitMQ real (mesma imagem da plataforma) com a topologia de {@code definitions.json} importada no boot: exchanges,
 * filas quorum com {@code x-delivery-limit} e DLQs idênticas às do ambiente. O serviço não declara filas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitMqContainerConfig {

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMqContainer() {
        return new RabbitMQContainer("rabbitmq:4.3-management-alpine")
                .withAdminUser("oficina")
                .withAdminPassword("oficina")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("rabbitmq/definitions.json"),
                        "/etc/rabbitmq/definitions.json")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("rabbitmq/rabbitmq.conf"),
                        "/etc/rabbitmq/conf.d/90-oficina.conf");
    }
}
