package io.github.gabrielivo.oficina.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** Configuração real de application.properties (sem auto-configuração): segurança ligada por padrão. */
class PropriedadesSegurancaBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SegurancaProperties.class, JwtProperties.class, InternalApiProperties.class})
    static class SoPropriedades {}

    private static ConfigurableApplicationContext iniciar(String... perfis) {
        return new SpringApplicationBuilder(SoPropriedades.class)
                .web(WebApplicationType.NONE)
                .profiles(perfis)
                .run();
    }

    @Test
    void deveLigarSegurancaPorPadraoEVincularJwtEChaveInterna() {
        try (ConfigurableApplicationContext contexto = iniciar()) {
            assertThat(contexto.getBean(SegurancaProperties.class).enabled()).isTrue();
            assertThat(contexto.getBean(JwtProperties.class).secret()).isNotBlank();
            assertThat(contexto.getBean(JwtProperties.class).expiration()).isPositive();
            assertThat(contexto.getBean(InternalApiProperties.class).apiKey()).isNotBlank();
        }
    }

    @Test
    void deveDesligarSegurancaNoPerfilLocal() {
        try (ConfigurableApplicationContext contexto = iniciar("local")) {
            assertThat(contexto.getBean(SegurancaProperties.class).enabled()).isFalse();
            assertThat(contexto.getBean(JwtProperties.class).secret()).isNotBlank();
        }
    }
}
