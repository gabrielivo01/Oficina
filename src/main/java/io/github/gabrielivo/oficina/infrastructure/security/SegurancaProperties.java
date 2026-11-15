package io.github.gabrielivo.oficina.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.security.enabled}: segurança (JWT + API key interna) <b>ligada por padrão</b>. Só o perfil {@code local}
 * a desliga explicitamente (ver {@code application.properties}).
 */
@ConfigurationProperties(prefix = "app.security")
public record SegurancaProperties(Boolean enabled) {

    public SegurancaProperties {
        enabled = enabled == null || enabled;
    }
}
