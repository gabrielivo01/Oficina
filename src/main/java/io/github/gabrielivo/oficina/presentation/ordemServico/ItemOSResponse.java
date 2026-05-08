package io.github.gabrielivo.oficina.presentation.ordemServico;


import  io.github.gabrielivo.oficina.domain.ordemServico.TipoItemOrdemServico;
import java.math.BigDecimal;

public record ItemOSResponse(
    String id,
    String descricao,
    TipoItemOrdemServico tipo,
    BigDecimal valor,
    Integer quantidade,
    BigDecimal valorTotal,
    String pecaId,
    String nomePeca
) {}