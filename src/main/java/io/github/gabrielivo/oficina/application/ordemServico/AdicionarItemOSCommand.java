package io.github.gabrielivo.oficina.application.ordemServico;


import java.math.BigDecimal;

import io.github.gabrielivo.oficina.domain.ordemServico.TipoItemOrdemServico;

public record AdicionarItemOSCommand(
    String ordemServicoId,
    String descricao,
    TipoItemOrdemServico tipo,
    BigDecimal valor,
    Integer quantidade,
    String pecaId
) {}
