package io.github.gabrielivo.oficina.presentation.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AtualizarClienteRequest(
        @NotBlank(message = "Nome é obrigatório") String nome,
        String telefone,
        @Email(message = "E-mail inválido") String email,
        EnderecoRequest endereco) {
    public AtualizarClienteRequest(String nome, String telefone, EnderecoRequest endereco) {
        this(nome, telefone, null, endereco);
    }
}
