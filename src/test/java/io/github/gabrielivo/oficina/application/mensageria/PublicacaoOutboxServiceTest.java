package io.github.gabrielivo.oficina.application.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutbox;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicacaoOutboxServiceTest {

    private OutboxService outboxService;
    private PublicadorMensagens publicador;
    private SimpleMeterRegistry meterRegistry;
    private PublicacaoOutboxService publicacao;

    @BeforeEach
    void setUp() {
        outboxService = mock(OutboxService.class);
        publicador = mock(PublicadorMensagens.class);
        meterRegistry = new SimpleMeterRegistry();
        MensageriaProperties properties = new MensageriaProperties(
                "oficina.eventos",
                "oficina.comandos",
                "os.saga.eventos",
                new MensageriaProperties.Outbox(true, Duration.ofSeconds(1), 10, Duration.ofSeconds(5)));
        publicacao = new PublicacaoOutboxService(outboxService, publicador, properties, meterRegistry);
    }

    private static MensagemOutbox mensagem(String id) {
        return new MensagemOutbox(id, "StatusOrdemServicoAlterado", "oficina.eventos", "os.status-alterado", "{}");
    }

    @Test
    void deveMarcarComoEnviadaSoDepoisDaConfirmacao() {
        when(outboxService.buscarPendentes(10)).thenReturn(List.of(mensagem("a"), mensagem("b")));
        when(outboxService.contarPendentes()).thenReturn(0L);

        int enviadas = publicacao.publicarPendentes();

        assertThat(enviadas).isEqualTo(2);
        verify(publicador)
                .publicar(argThat(m -> m.messageId().equals("a")
                        && m.exchange().equals("oficina.eventos")
                        && m.routingKey().equals("os.status-alterado")
                        && m.corpo().equals("{}")));
        verify(outboxService).marcarEnviada("a");
        verify(outboxService).marcarEnviada("b");
        assertThat(meterRegistry.counter("oficina.outbox.publicadas").count()).isEqualTo(2.0);
    }

    @Test
    void deveRegistrarFalhaEPararNoPrimeiroErroParaPreservarAOrdem() {
        when(outboxService.buscarPendentes(10)).thenReturn(List.of(mensagem("a"), mensagem("b")));
        when(outboxService.contarPendentes()).thenReturn(2L);
        doThrow(new FalhaPublicacaoException("nack")).when(publicador).publicar(any());

        int enviadas = publicacao.publicarPendentes();

        assertThat(enviadas).isZero();
        verify(outboxService).registrarFalha("a", "nack");
        verify(outboxService, never()).marcarEnviada(any());
        verify(publicador).publicar(argThat(m -> m.messageId().equals("a")));
        verify(publicador, never()).publicar(argThat(m -> m.messageId().equals("b")));
        assertThat(meterRegistry.counter("oficina.outbox.falhas").count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("oficina.outbox.pendentes").gauge().value())
                .isEqualTo(2.0);
    }

    @Test
    void naoFazNadaSemPendentes() {
        when(outboxService.buscarPendentes(10)).thenReturn(List.of());

        assertThat(publicacao.publicarPendentes()).isZero();
        verify(publicador, never()).publicar(any());
    }
}
