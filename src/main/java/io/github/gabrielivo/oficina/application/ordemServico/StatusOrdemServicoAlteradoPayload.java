package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.time.Instant;

/**
 * Payload do evento {@code StatusOrdemServicoAlterado} (ver {@code documentacao/contratos_mensagens.md}): publicado pelo
 * OS Service em {@code oficina.eventos} a cada transição de status da OS.
 */
public record StatusOrdemServicoAlteradoPayload(
        StatusOrdemServico statusAnterior,
        StatusOrdemServico statusNovo,
        OrigemMudancaStatus origem,
        String motivo,
        Instant alteradoEm) {

    public static final String TIPO = "StatusOrdemServicoAlterado";
    public static final String ROUTING_KEY = "os.status-alterado";
}
