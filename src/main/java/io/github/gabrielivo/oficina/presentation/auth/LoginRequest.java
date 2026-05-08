package io.github.gabrielivo.oficina.presentation.auth;


import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Login é obrigatório")
    String login,

    @NotBlank(message = "Senha é obrigatória")
    String senha
) {}
