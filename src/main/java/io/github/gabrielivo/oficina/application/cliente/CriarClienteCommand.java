package io.github.gabrielivo.oficina.application.cliente;


public record CriarClienteCommand(
    String cpf,
    String nome,
    String telefone,
    EnderecoCommand endereco
) {}