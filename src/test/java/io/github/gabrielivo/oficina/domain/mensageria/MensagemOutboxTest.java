package io.github.gabrielivo.oficina.domain.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MensagemOutboxTest {

    private static MensagemOutbox nova() {
        return new MensagemOutbox("id-1", "Tipo", "oficina.eventos", "rk", "{}");
    }

    @Test
    void deveNascerPendenteENova() {
        MensagemOutbox mensagem = nova();

        assertThat(mensagem.isEnviada()).isFalse();
        assertThat(mensagem.isNew()).isTrue();
        assertThat(mensagem.getTentativas()).isZero();
        assertThat(mensagem.getCriadoEm()).isNotNull();
    }

    @Test
    void deveExigirCamposObrigatorios() {
        assertThrows(IllegalArgumentException.class, () -> new MensagemOutbox(" ", "Tipo", "ex", "rk", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new MensagemOutbox("id", null, "ex", "rk", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new MensagemOutbox("id", "Tipo", "", "rk", "{}"));
        assertThrows(IllegalArgumentException.class, () -> new MensagemOutbox("id", "Tipo", "ex", null, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new MensagemOutbox("id", "Tipo", "ex", "rk", ""));
    }

    @Test
    void deveMarcarEnviadaUmaVezSoELimparErro() {
        MensagemOutbox mensagem = nova();
        mensagem.registrarFalha("broker fora");

        mensagem.marcarEnviada();
        LocalDateTime enviadaEm = mensagem.getEnviadoEm();
        mensagem.marcarEnviada();

        assertThat(mensagem.isEnviada()).isTrue();
        assertThat(mensagem.getEnviadoEm()).isEqualTo(enviadaEm);
        assertThat(mensagem.getTentativas()).isEqualTo(2);
        assertThat(mensagem.getUltimoErro()).isNull();
    }

    @Test
    void deveContarCadaFalhaEUsarMensagemPadraoQuandoErroSemTexto() {
        MensagemOutbox mensagem = nova();

        mensagem.registrarFalha("x".repeat(800));
        mensagem.registrarFalha(null);

        assertThat(mensagem.isEnviada()).isFalse();
        assertThat(mensagem.getTentativas()).isEqualTo(2);
        assertThat(mensagem.getUltimoErro()).isEqualTo("Falha sem mensagem");
    }

    @Test
    void deveTruncarErroEm500Caracteres() {
        MensagemOutbox mensagem = nova();

        mensagem.registrarFalha("y".repeat(800));

        assertThat(mensagem.getUltimoErro()).hasSize(500);
    }

    @Test
    void deixaDeSerNovaDepoisDePersistida() {
        MensagemOutbox mensagem = nova();

        mensagem.marcarPersistida();

        assertThat(mensagem.isNew()).isFalse();
    }
}
