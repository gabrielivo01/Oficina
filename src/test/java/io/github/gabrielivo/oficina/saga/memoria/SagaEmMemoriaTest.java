package io.github.gabrielivo.oficina.saga.memoria;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.MotivoCompensacao;
import io.github.gabrielivo.oficina.domain.saga.SituacaoSaga;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Saga de ponta a ponta em memória (Etapa 5.4): orquestrador, outbox e inbox reais trocando comandos e eventos do
 * catálogo com participantes falsos. Cobre o caminho feliz e as três compensações da seção 4.2 do plano.
 */
class SagaEmMemoriaTest {

    private SagaEmMemoria saga;

    @BeforeEach
    void criarSaga() {
        saga = new SagaEmMemoria();
    }

    @Test
    void deveLevarAOsDeRecebidaAteEntregueNoCaminhoFeliz() {
        String osId = saga.abrirOrdemServico();
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.RECEBIDA);
        assertThat(saga.execution().comandosRecebidos(osId)).containsExactly("SolicitarDiagnostico");

        saga.concluirDiagnostico(osId);
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
        assertThat(saga.ordem(osId).getItens()).hasSize(2);
        assertThat(saga.ordem(osId).getValorTotal()).isEqualByComparingTo("590.50");

        saga.responderOrcamento(osId, true);
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.AGUARDANDO_PAGAMENTO);

        saga.confirmarPagamento(osId);
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.EM_EXECUCAO);

        saga.finalizarExecucao(osId);
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.FINALIZADA);
        assertThat(saga.situacao(osId)).isEqualTo(SituacaoSaga.CONCLUIDA);

        saga.entregar(osId);
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.ENTREGUE);
        assertThat(saga.billing().comandosRecebidos(osId)).containsExactly("GerarOrcamento", "CriarCobranca");
        assertThat(saga.execution().comandosRecebidos(osId)).containsExactly("SolicitarDiagnostico", "IniciarExecucao");
        assertThat(saga.notificacoes(osId))
                .containsExactly(
                        "EM_DIAGNOSTICO",
                        "AGUARDANDO_APROVACAO",
                        "AGUARDANDO_PAGAMENTO",
                        "EM_EXECUCAO",
                        "FINALIZADA",
                        "ENTREGUE");
        assertThat(saga.mensagensPendentesNoOutbox()).isZero();
    }

    @Test
    void orcamentoRecusadoDeveLiberarAReservaECancelarAOs() {
        String osId = saga.abrirOrdemServico();
        saga.concluirDiagnostico(osId);

        saga.responderOrcamento(osId, false);

        assertThat(saga.execution().comandosRecebidos(osId))
                .containsExactly("SolicitarDiagnostico", "LiberarReservaPecas");
        assertThat(saga.billing().comandosRecebidos(osId)).containsExactly("GerarOrcamento");
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.CANCELADA);
        assertThat(saga.situacao(osId)).isEqualTo(SituacaoSaga.COMPENSADA);
        assertThat(saga.saga(osId).getMotivoCompensacao()).isEqualTo(MotivoCompensacao.ORCAMENTO_RECUSADO);
    }

    @Test
    void pagamentoRejeitadoDeveLiberarAReservaECancelarAOs() {
        String osId = aguardandoPagamento();

        saga.rejeitarPagamento(osId);

        assertThat(saga.execution().comandosRecebidos(osId))
                .containsExactly("SolicitarDiagnostico", "LiberarReservaPecas");
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.CANCELADA);
        assertThat(saga.situacao(osId)).isEqualTo(SituacaoSaga.COMPENSADA);
        assertThat(saga.saga(osId).getMotivoCompensacao()).isEqualTo(MotivoCompensacao.PAGAMENTO_REJEITADO);
    }

    @Test
    void pagamentoExpiradoDeveCancelarACobrancaLiberarAReservaECancelarAOs() {
        saga.billing().prazoPagamento(Duration.ZERO);
        String osId = aguardandoPagamento();

        saga.verificarPrazos();

        assertThat(saga.billing().comandosRecebidos(osId))
                .containsExactly("GerarOrcamento", "CriarCobranca", "CancelarCobranca");
        assertThat(saga.execution().comandosRecebidos(osId))
                .containsExactly("SolicitarDiagnostico", "LiberarReservaPecas");
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.CANCELADA);
        assertThat(saga.situacao(osId)).isEqualTo(SituacaoSaga.COMPENSADA);
        assertThat(saga.saga(osId).getMotivoCompensacao()).isEqualTo(MotivoCompensacao.PRAZO_PAGAMENTO_EXPIRADO);
    }

    @Test
    void execucaoRecusadaDeveEstornarOPagamentoLiberarAReservaECancelarAOs() {
        saga.execution().recusarInicioExecucao();
        String osId = aguardandoPagamento();

        saga.confirmarPagamento(osId);

        assertThat(saga.billing().comandosRecebidos(osId))
                .containsExactly("GerarOrcamento", "CriarCobranca", "EstornarPagamento");
        assertThat(saga.execution().comandosRecebidos(osId))
                .containsExactly("SolicitarDiagnostico", "IniciarExecucao", "LiberarReservaPecas");
        assertThat(saga.barramento().eventosEntregues(osId))
                .containsSubsequence("ExecucaoRecusada", "PagamentoEstornado", "ReservaPecasLiberada");
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.CANCELADA);
        assertThat(saga.situacao(osId)).isEqualTo(SituacaoSaga.COMPENSADA);
        assertThat(saga.saga(osId).getMotivoCompensacao()).isEqualTo(MotivoCompensacao.EXECUCAO_RECUSADA);
    }

    @Test
    void eventosEntreguesEmDuplicidadeNaoDevemAlterarOResultado() {
        saga.entregarEventosEmDuplicidade();
        String osId = aguardandoPagamento();
        saga.confirmarPagamento(osId);
        saga.finalizarExecucao(osId);

        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.FINALIZADA);
        assertThat(saga.situacao(osId)).isEqualTo(SituacaoSaga.CONCLUIDA);
        assertThat(saga.billing().comandosRecebidos(osId)).containsExactly("GerarOrcamento", "CriarCobranca");
        assertThat(saga.execution().comandosRecebidos(osId)).containsExactly("SolicitarDiagnostico", "IniciarExecucao");
        assertThat(saga.notificacoes(osId)).doesNotHaveDuplicates();
    }

    @Test
    void cancelamentoManualDuranteOPagamentoDeveCancelarACobrancaELiberarAReserva() {
        String osId = aguardandoPagamento();

        saga.cancelar(osId, "Cliente desistiu");

        assertThat(saga.billing().comandosRecebidos(osId))
                .containsExactly("GerarOrcamento", "CriarCobranca", "CancelarCobranca");
        assertThat(saga.execution().comandosRecebidos(osId))
                .containsExactly("SolicitarDiagnostico", "LiberarReservaPecas");
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.CANCELADA);
        assertThat(saga.situacao(osId)).isEqualTo(SituacaoSaga.COMPENSADA);
    }

    private String aguardandoPagamento() {
        String osId = saga.abrirOrdemServico();
        saga.concluirDiagnostico(osId);
        saga.responderOrcamento(osId, true);
        assertThat(saga.status(osId)).isEqualTo(StatusOrdemServico.AGUARDANDO_PAGAMENTO);
        return osId;
    }
}
