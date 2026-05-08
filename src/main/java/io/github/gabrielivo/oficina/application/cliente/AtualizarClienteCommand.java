package io.github.gabrielivo.oficina.application.cliente;

public record AtualizarClienteCommand(
    String nome,
    String telefone,
    EnderecoCommand endereco
) {}