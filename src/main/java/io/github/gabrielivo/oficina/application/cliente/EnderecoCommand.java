package io.github.gabrielivo.oficina.application.cliente;

public record EnderecoCommand(
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf
) {}