package io.github.gabrielivo.oficina.infrastructure.notification;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;

public interface StatusNotificationPort {
    void enviarAtualizacao(String ordemServicoId, StatusOrdemServico status, String mensagem);
}
