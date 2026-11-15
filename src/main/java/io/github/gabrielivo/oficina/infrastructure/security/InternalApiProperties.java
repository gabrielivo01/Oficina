package io.github.gabrielivo.oficina.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.internal.api-key}: chave exigida no header {@code X-Internal-Api-Key} pelos endpoints {@code /internal/**}
 * (contrato com a oficina-auth-lambda). Valor só por variável de ambiente fora do perfil padrão.
 */
@ConfigurationProperties(prefix = "app.internal")
public record InternalApiProperties(String apiKey) {}
