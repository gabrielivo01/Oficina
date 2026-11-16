package io.github.gabrielivo.oficina.application.ordemServico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NotificacaoStatusOrdemServicoManipuladorTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final StatusNotificationPort port = mock(StatusNotificationPort.class);
    private final NotificacaoStatusOrdemServicoManipulador manipulador =
            new NotificacaoStatusOrdemServicoManipulador(port, jsonMapper);

    private EnvelopeMensagem envelope(String motivo) {
        var payload = new StatusOrdemServicoAlteradoPayload(
                StatusOrdemServico.EM_DIAGNOSTICO,
                StatusOrdemServico.CANCELADA,
                OrigemMudancaStatus.USUARIO,
                motivo,
                Instant.parse("2026-11-15T20:00:00Z"));
        return new EnvelopeMensagem(
                "msg-1",
                StatusOrdemServicoAlteradoPayload.TIPO,
                "os-1",
                "os-1",
                Instant.now(),
                jsonMapper.valueToTree(payload));
    }

    @Test
    void deveTratarOEventoDeStatusAlterado() {
        assertThat(manipulador.tipo()).isEqualTo("StatusOrdemServicoAlterado");
    }

    @Test
    void deveNotificarNovoStatusComMotivo() {
        manipulador.manipular(envelope("Cliente desistiu"));

        verify(port)
                .enviarAtualizacao(
                        "os-1",
                        StatusOrdemServico.CANCELADA,
                        "Status alterado de EM_DIAGNOSTICO para CANCELADA. Motivo: Cliente desistiu");
    }

    @Test
    void deveNotificarSemMotivoQuandoAusente() {
        manipulador.manipular(envelope(null));

        verify(port)
                .enviarAtualizacao(
                        "os-1", StatusOrdemServico.CANCELADA, "Status alterado de EM_DIAGNOSTICO para CANCELADA");
    }
}
