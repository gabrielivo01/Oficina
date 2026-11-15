package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;

/**
 * Porta de notificação de mudança de status da OS ao cliente. Declarada em application (quem a usa); os adapters
 * (e-mail, no-op) ficam em {@code infrastructure/notification}.
 */
public interface StatusNotificationPort {
    void enviarAtualizacao(String ordemServicoId, StatusOrdemServico status, String mensagem);
}
