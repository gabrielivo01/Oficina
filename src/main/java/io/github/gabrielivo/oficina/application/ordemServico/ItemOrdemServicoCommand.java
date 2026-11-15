package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.TipoItemOrdemServico;
import java.math.BigDecimal;

public record ItemOrdemServicoCommand(
        String descricao, TipoItemOrdemServico tipo, BigDecimal valor, Integer quantidade, String pecaId) {}
