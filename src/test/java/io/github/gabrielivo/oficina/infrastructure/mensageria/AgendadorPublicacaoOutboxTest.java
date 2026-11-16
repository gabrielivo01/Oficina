package io.github.gabrielivo.oficina.infrastructure.mensageria;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gabrielivo.oficina.application.mensageria.PublicacaoOutboxService;
import org.junit.jupiter.api.Test;

class AgendadorPublicacaoOutboxTest {

    private final PublicacaoOutboxService publicacao = mock(PublicacaoOutboxService.class);
    private final AgendadorPublicacaoOutbox agendador = new AgendadorPublicacaoOutbox(publicacao);

    @Test
    void deveDispararPublicacaoDosPendentes() {
        agendador.publicar();

        verify(publicacao).publicarPendentes();
    }

    @Test
    void naoDeveDerrubarOAgendamentoQuandoCicloFalha() {
        when(publicacao.publicarPendentes()).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(agendador::publicar).doesNotThrowAnyException();
    }
}
