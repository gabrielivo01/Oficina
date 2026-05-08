package io.github.gabrielivo.oficina.presentation.cliente;


import io.github.gabrielivo.oficina.shared.util.CpfValido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
    @NotBlank(message = "CPF é obrigatório")
    @Size(min = 11, max = 11, message = "CPF deve ter 11 dígitos")
    @CpfValido                           
    String cpf,

    @NotBlank(message = "Nome é obrigatório")
    String nome,

    String telefone,
    EnderecoRequest endereco
) {}