package io.github.gabrielivo.oficina.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code jwt.secret} (HS256, ≥ 256 bits, compartilhado com a Lambda de CPF) e {@code jwt.expiration} em ms. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expiration) {}
