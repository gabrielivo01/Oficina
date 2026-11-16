package io.github.gabrielivo.oficina.domain.ordemServico;

import java.time.LocalDateTime;

/**
 * Evento de domínio registrado pelo agregado a cada transição de status (não na abertura). O service o extrai com
 * {@link OrdemServico#extrairEventos()} e o grava no outbox na mesma transação.
 */
public record StatusOrdemServicoAlterado(
        String ordemServicoId,
        StatusOrdemServico statusAnterior,
        StatusOrdemServico statusNovo,
        OrigemMudancaStatus origem,
        String motivo,
        LocalDateTime alteradoEm) {}
