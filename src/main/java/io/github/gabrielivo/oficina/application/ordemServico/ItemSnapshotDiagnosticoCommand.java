package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.TipoItemOrdemServico;
import java.math.BigDecimal;

public record ItemSnapshotDiagnosticoCommand(
        TipoItemOrdemServico tipo, String descricao, BigDecimal valorUnitario, int quantidade, String pecaId) {}
