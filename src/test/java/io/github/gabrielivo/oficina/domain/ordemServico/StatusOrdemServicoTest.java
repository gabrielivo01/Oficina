package io.github.gabrielivo.oficina.domain.ordemServico;

import static io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Máquina de estados da seção 3.1 do plano da Fase 4. */
class StatusOrdemServicoTest {

    private static final Map<StatusOrdemServico, Set<StatusOrdemServico>> TRANSICOES_VALIDAS = Map.of(
            RECEBIDA, EnumSet.of(EM_DIAGNOSTICO, CANCELADA),
            EM_DIAGNOSTICO, EnumSet.of(AGUARDANDO_APROVACAO, CANCELADA),
            AGUARDANDO_APROVACAO, EnumSet.of(AGUARDANDO_PAGAMENTO, CANCELADA),
            AGUARDANDO_PAGAMENTO, EnumSet.of(EM_EXECUCAO, CANCELADA),
            EM_EXECUCAO, EnumSet.of(FINALIZADA, CANCELADA),
            FINALIZADA, EnumSet.of(ENTREGUE),
            ENTREGUE, EnumSet.noneOf(StatusOrdemServico.class),
            CANCELADA, EnumSet.noneOf(StatusOrdemServico.class));

    @ParameterizedTest
    @EnumSource(StatusOrdemServico.class)
    void devePermitirSomenteAsTransicoesDaMaquinaDeEstados(StatusOrdemServico origem) {
        for (StatusOrdemServico destino : StatusOrdemServico.values()) {
            boolean esperado = TRANSICOES_VALIDAS.get(origem).contains(destino);
            assertThat(origem.podeTransicionarPara(destino))
                    .as("%s -> %s", origem, destino)
                    .isEqualTo(esperado);
        }
    }

    @Test
    void naoDevePermitirTransicaoParaNulo() {
        assertThat(RECEBIDA.podeTransicionarPara(null)).isFalse();
    }

    @Test
    void deveSeguirOFluxoLinearAteEntregue() {
        assertThat(RECEBIDA.proximoStatus()).isEqualTo(EM_DIAGNOSTICO);
        assertThat(EM_DIAGNOSTICO.proximoStatus()).isEqualTo(AGUARDANDO_APROVACAO);
        assertThat(AGUARDANDO_APROVACAO.proximoStatus()).isEqualTo(AGUARDANDO_PAGAMENTO);
        assertThat(AGUARDANDO_PAGAMENTO.proximoStatus()).isEqualTo(EM_EXECUCAO);
        assertThat(EM_EXECUCAO.proximoStatus()).isEqualTo(FINALIZADA);
        assertThat(FINALIZADA.proximoStatus()).isEqualTo(ENTREGUE);
        assertThat(ENTREGUE.proximoStatus()).isNull();
        assertThat(CANCELADA.proximoStatus()).isNull();
    }

    @Test
    void deveConsiderarAtivosSomenteOsStatusNaoEncerrados() {
        assertThat(StatusOrdemServico.ativos())
                .containsExactlyInAnyOrder(
                        RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, AGUARDANDO_PAGAMENTO, EM_EXECUCAO);
    }
}
