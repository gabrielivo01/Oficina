package io.github.gabrielivo.oficina.presentation.cliente;

import io.github.gabrielivo.oficina.domain.cliente.StatusCliente;

import java.time.LocalDateTime;

public record ClienteResponse(
    String id,
    String cpf,
    String nome,
    String telefone,
    EnderecoResponse endereco,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm,
    StatusCliente status
) {}