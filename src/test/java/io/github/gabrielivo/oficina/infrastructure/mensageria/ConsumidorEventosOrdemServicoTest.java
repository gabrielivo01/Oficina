package io.github.gabrielivo.oficina.infrastructure.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.MensagemNaoSuportadaException;
import io.github.gabrielivo.oficina.application.mensageria.ProcessadorMensagensService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

class ConsumidorEventosOrdemServicoTest {

    private final ProcessadorMensagensService processador = mock(ProcessadorMensagensService.class);
    private final ConsumidorEventosOrdemServico consumidor =
            new ConsumidorEventosOrdemServico(processador, JsonMapper.builder().build());

    private static Message mensagem(String corpo) {
        return new Message(corpo.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void deveConverterEnvelopeEDelegarAoProcessador() {
        consumidor.receber(mensagem("""
                {"messageId":"m-1","type":"StatusOrdemServicoAlterado","sagaId":"os-1","osId":"os-1",
                 "occurredAt":"2026-11-15T20:00:00Z","payload":{"statusNovo":"CANCELADA"}}
                """));

        ArgumentCaptor<EnvelopeMensagem> captor = ArgumentCaptor.forClass(EnvelopeMensagem.class);
        verify(processador).processar(captor.capture());
        EnvelopeMensagem envelope = captor.getValue();
        assertThat(envelope.messageId()).isEqualTo("m-1");
        assertThat(envelope.type()).isEqualTo("StatusOrdemServicoAlterado");
        assertThat(envelope.osId()).isEqualTo("os-1");
        assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-11-15T20:00:00Z"));
        assertThat(envelope.payload().get("statusNovo").asString()).isEqualTo("CANCELADA");
    }

    @Test
    void deveRejeitarSemRequeueJsonInvalido() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumidor.receber(mensagem("não é json")));
        verify(processador, never()).processar(any());
    }

    @Test
    void deveRejeitarSemRequeueEnvelopeSemMessageId() {
        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumidor.receber(mensagem("{\"type\":\"StatusOrdemServicoAlterado\"}")));
    }

    @Test
    void deveRejeitarSemRequeueTipoSemManipulador() {
        when(processador.processar(any())).thenThrow(new MensagemNaoSuportadaException("Outro"));

        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumidor.receber(mensagem("{\"messageId\":\"m-1\",\"type\":\"Outro\"}")));
    }

    @Test
    void devePropagarFalhaTransitoriaParaRetry() {
        when(processador.processar(any())).thenThrow(new IllegalStateException("banco fora"));

        IllegalStateException erro = assertThrows(
                IllegalStateException.class,
                () -> consumidor.receber(mensagem("{\"messageId\":\"m-1\",\"type\":\"StatusOrdemServicoAlterado\"}")));
        assertThat(erro).hasMessage("banco fora");
    }
}
