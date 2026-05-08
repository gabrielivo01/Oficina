package io.github.gabrielivo.oficina.domain.ordemServico;


import java.math.BigDecimal;

public record AdicionarItemCommand(
    String ordemServicoId,
    String descricao,
    TipoItemOrdemServico tipo,
    BigDecimal valor,
    Integer quantidade,
    String pecaId
) {}