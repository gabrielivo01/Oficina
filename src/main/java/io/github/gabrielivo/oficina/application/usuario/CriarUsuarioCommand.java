package io.github.gabrielivo.oficina.application.usuario;

public record CriarUsuarioCommand(
    String login,
    String senha
) {}
