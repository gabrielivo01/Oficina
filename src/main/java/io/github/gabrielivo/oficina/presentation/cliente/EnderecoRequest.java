package io.github.gabrielivo.oficina.presentation.cliente;

public record EnderecoRequest(
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf
) {}