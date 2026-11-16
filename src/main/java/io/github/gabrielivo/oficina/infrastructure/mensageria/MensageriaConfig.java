package io.github.gabrielivo.oficina.infrastructure.mensageria;

import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mensageria (Spring AMQP). A topologia não é declarada aqui: vem de
 * {@code oficina-infra-k8s/local/rabbitmq/definitions.json}. Publisher confirms/returns, retry do consumidor e
 * observação ficam em {@code application.properties} ({@code spring.rabbitmq.*}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MensageriaProperties.class)
public class MensageriaConfig {}
