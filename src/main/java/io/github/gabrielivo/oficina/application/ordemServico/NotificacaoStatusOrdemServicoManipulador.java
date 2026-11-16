package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.ManipuladorMensagem;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envia a notificação (e-mail) de mudança de status ao consumir {@code StatusOrdemServicoAlterado}. Como o evento só é
 * publicado pelo outbox depois do commit, o e-mail nunca sai por uma mudança que foi desfeita; o inbox evita o
 * reenvio em mensagens duplicadas.
 */
@Component
public class NotificacaoStatusOrdemServicoManipulador implements ManipuladorMensagem {

    private final StatusNotificationPort statusNotificationPort;
    private final JsonMapper jsonMapper;

    public NotificacaoStatusOrdemServicoManipulador(
            StatusNotificationPort statusNotificationPort, JsonMapper jsonMapper) {
        this.statusNotificationPort = statusNotificationPort;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String tipo() {
        return StatusOrdemServicoAlteradoPayload.TIPO;
    }

    @Override
    public void manipular(EnvelopeMensagem envelope) {
        StatusOrdemServicoAlteradoPayload payload =
                jsonMapper.treeToValue(envelope.payload(), StatusOrdemServicoAlteradoPayload.class);
        String mensagem = "Status alterado de " + payload.statusAnterior() + " para " + payload.statusNovo()
                + (payload.motivo() == null ? "" : ". Motivo: " + payload.motivo());
        statusNotificationPort.enviarAtualizacao(envelope.osId(), payload.statusNovo(), mensagem);
    }
}
