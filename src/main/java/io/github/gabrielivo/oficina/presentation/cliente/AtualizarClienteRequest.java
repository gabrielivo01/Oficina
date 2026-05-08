package io.github.gabrielivo.oficina.presentation.cliente;

import jakarta.validation.constraints.NotBlank;

public record AtualizarClienteRequest(
    @NotBlank(message = "Nome é obrigatório")
    String nome,

    String telefone,
    EnderecoRequest endereco
) {}
