package io.github.gabrielivo.oficina.application.ordemServico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListarOrdensServicoCommandTest {

    @Test
    void deveUsarStatusAtivosQuandoFiltroNaoInformado() {
        assertThat(new ListarOrdensServicoCommand(null, 0, 20).status()).isEqualTo(StatusOrdemServico.ativos());
        assertThat(new ListarOrdensServicoCommand(Set.of(), 0, 20).status()).isEqualTo(StatusOrdemServico.ativos());
    }

    @Test
    void deveManterFiltroDeStatusInformado() {
        var command = new ListarOrdensServicoCommand(Set.of(StatusOrdemServico.CANCELADA), 2, 5);

        assertThat(command.status()).containsExactly(StatusOrdemServico.CANCELADA);
        assertThat(command.pagina()).isEqualTo(2);
        assertThat(command.tamanho()).isEqualTo(5);
    }

    @Test
    void deveRejeitarPaginaNegativa() {
        assertThrows(IllegalArgumentException.class, () -> new ListarOrdensServicoCommand(null, -1, 20));
    }

    @Test
    void deveRejeitarTamanhoForaDosLimites() {
        assertThrows(IllegalArgumentException.class, () -> new ListarOrdensServicoCommand(null, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ListarOrdensServicoCommand(null, 0, ListarOrdensServicoCommand.TAMANHO_MAXIMO + 1));
    }
}
