package io.github.gabrielivo.oficina.application.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gabrielivo.oficina.domain.mensageria.MensagemProcessada;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemProcessadaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ProcessadorMensagensServiceTest {

    private MensagemProcessadaRepository inbox;
    private ManipuladorMensagem manipulador;
    private ProcessadorMensagensService processador;

    private static EnvelopeMensagem envelope(String messageId, String tipo) {
        return new EnvelopeMensagem(messageId, tipo, "os-1", "os-1", Instant.now(), null);
    }

    @BeforeEach
    void setUp() {
        inbox = mock(MensagemProcessadaRepository.class);
        manipulador = mock(ManipuladorMensagem.class);
        when(manipulador.tipo()).thenReturn("OrcamentoAprovado");
        processador = new ProcessadorMensagensService(inbox, List.of(manipulador));
    }

    @Test
    void deveRegistrarNoInboxAntesDeManipularMensagemNova() {
        EnvelopeMensagem mensagem = envelope("msg-1", "OrcamentoAprovado");
        when(inbox.existsById("msg-1")).thenReturn(false);

        boolean processou = processador.processar(mensagem);

        assertThat(processou).isTrue();
        InOrder ordem = inOrder(inbox, manipulador);
        ArgumentCaptor<MensagemProcessada> registro = ArgumentCaptor.forClass(MensagemProcessada.class);
        ordem.verify(inbox).saveAndFlush(registro.capture());
        ordem.verify(manipulador).manipular(mensagem);
        assertThat(registro.getValue().getMessageId()).isEqualTo("msg-1");
        assertThat(registro.getValue().getTipo()).isEqualTo("OrcamentoAprovado");
    }

    @Test
    void deveIgnorarMensagemDuplicada() {
        when(inbox.existsById("msg-1")).thenReturn(true);

        boolean processou = processador.processar(envelope("msg-1", "OrcamentoAprovado"));

        assertThat(processou).isFalse();
        verify(inbox, never()).saveAndFlush(any());
        verify(manipulador, never()).manipular(any());
    }

    @Test
    void deveRecusarTipoSemManipuladorSemTocarNoInbox() {
        assertThrows(MensagemNaoSuportadaException.class, () -> processador.processar(envelope("msg-1", "Outro")));

        verify(inbox, never()).existsById(any());
        verify(inbox, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveAceitarDoisManipuladoresParaOMesmoTipo() {
        ManipuladorMensagem repetido = mock(ManipuladorMensagem.class);
        when(repetido.tipo()).thenReturn("OrcamentoAprovado");

        assertThrows(
                IllegalStateException.class,
                () -> new ProcessadorMensagensService(inbox, List.of(manipulador, repetido)));
    }

    @Test
    void envelopeExigeMessageIdETipo() {
        assertThrows(IllegalArgumentException.class, () -> envelope(" ", "Tipo"));
        assertThrows(IllegalArgumentException.class, () -> envelope("msg-1", null));
    }
}
