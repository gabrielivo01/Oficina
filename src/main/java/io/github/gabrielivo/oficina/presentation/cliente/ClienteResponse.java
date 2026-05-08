package io.github.gabrielivo.oficina.presentation.cliente;

import java.time.LocalDateTime;

public record ClienteResponse(
    String id,
    String cpf,
    String nome,
    String telefone,
    EnderecoResponse endereco,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm
) {}