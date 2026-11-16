package io.github.gabrielivo.oficina.infrastructure.saga;

import io.github.gabrielivo.oficina.application.ordemServico.SagaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SagaProperties.class)
public class SagaConfig {}
