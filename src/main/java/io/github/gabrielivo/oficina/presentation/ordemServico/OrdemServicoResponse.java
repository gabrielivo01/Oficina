package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrdemServicoResponse(
        String id,
        String clienteId,
        String nomeCliente,
        String veiculoId,
        String placaVeiculo,
        StatusOrdemServico status,
        BigDecimal valorTotal,
        List<ItemOSResponse> itens,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {}
