package io.github.gabrielivo.oficina.infrastructure.notification;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class NoopStatusNotificationPort implements StatusNotificationPort {
    @Override
    public void enviarAtualizacao(String ordemServicoId, StatusOrdemServico status, String mensagem) {
        // Implementação padrão sem efeito para ambiente local/teste.
    }
}
