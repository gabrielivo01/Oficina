package io.github.gabrielivo.oficina.application.usuario;

public record TrocarSenhaCommand(
    String login,
    String senhaAtual,
    String novaSenha
) {}
