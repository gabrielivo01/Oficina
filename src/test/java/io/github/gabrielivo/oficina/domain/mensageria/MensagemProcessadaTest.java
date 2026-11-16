package io.github.gabrielivo.oficina.domain.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MensagemProcessadaTest {

    @Test
    void deveRegistrarIdTipoEData() {
        MensagemProcessada processada = new MensagemProcessada("msg-1", "OrcamentoAprovado");

        assertThat(processada.getId()).isEqualTo("msg-1");
        assertThat(processada.getMessageId()).isEqualTo("msg-1");
        assertThat(processada.getTipo()).isEqualTo("OrcamentoAprovado");
        assertThat(processada.getProcessadoEm()).isNotNull();
        assertThat(processada.isNew()).isTrue();
    }

    @Test
    void deveRejeitarMessageIdVazioOuLongoDemais() {
        assertThrows(IllegalArgumentException.class, () -> new MensagemProcessada(" ", "Tipo"));
        assertThrows(IllegalArgumentException.class, () -> new MensagemProcessada(null, "Tipo"));
        assertThrows(IllegalArgumentException.class, () -> new MensagemProcessada("x".repeat(65), "Tipo"));
    }

    @Test
    void deveExigirTipo() {
        assertThrows(IllegalArgumentException.class, () -> new MensagemProcessada("msg-1", ""));
    }

    @Test
    void deixaDeSerNovaDepoisDePersistida() {
        MensagemProcessada processada = new MensagemProcessada("msg-1", "Tipo");

        processada.marcarPersistida();

        assertThat(processada.isNew()).isFalse();
    }
}
