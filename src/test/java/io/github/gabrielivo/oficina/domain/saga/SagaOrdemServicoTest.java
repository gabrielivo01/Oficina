package io.github.gabrielivo.oficina.domain.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SagaOrdemServicoTest {

    private static final Instant AGORA = Instant.parse("2026-11-16T10:00:00Z");
    private static final Duration PRAZO_PAGAMENTO = Duration.ofMinutes(30);
    private static final Duration PRAZO_COMPENSACAO = Duration.ofSeconds(30);

    @Test
    void percorreOCaminhoFelizAteConcluir() {
        SagaOrdemServico saga = novaSaga();

        assertEquals(
                "SolicitarDiagnostico",
                saga.iniciar(AGORA).comandos().getFirst().tipo());
        assertEquals(
                StatusOrdemServico.EM_DIAGNOSTICO,
                receber(saga, "DiagnosticoIniciado", Map.of()).novoStatus());
        assertEquals(
                "GerarOrcamento",
                receber(
                                saga,
                                "DiagnosticoConcluido",
                                Map.of("reservaId", "reserva-1", "valorTotal", new BigDecimal("25.00")))
                        .comandos()
                        .getFirst()
                        .tipo());
        assertEquals(
                StatusOrdemServico.AGUARDANDO_APROVACAO,
                receber(
                                saga,
                                "OrcamentoGerado",
                                Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")))
                        .novoStatus());
        assertEquals(
                "CriarCobranca",
                receber(
                                saga,
                                "OrcamentoAprovado",
                                Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")))
                        .comandos()
                        .getFirst()
                        .tipo());
        assertEquals(
                StatusOrdemServico.AGUARDANDO_PAGAMENTO,
                receber(
                                saga,
                                "CobrancaCriada",
                                Map.of(
                                        "cobrancaId",
                                        "cobranca-1",
                                        "expiraEm",
                                        AGORA.plus(PRAZO_PAGAMENTO).toString()))
                        .novoStatus());
        assertEquals(
                "IniciarExecucao",
                receber(
                                saga,
                                "PagamentoConfirmado",
                                Map.of("cobrancaId", "cobranca-1", "pagamentoIdExterno", "pagamento-1"))
                        .comandos()
                        .getFirst()
                        .tipo());
        assertEquals(
                StatusOrdemServico.EM_EXECUCAO,
                receber(saga, "ExecucaoIniciada", Map.of()).novoStatus());
        assertEquals(
                StatusOrdemServico.FINALIZADA,
                receber(saga, "ExecucaoFinalizada", Map.of()).novoStatus());
        assertEquals(SituacaoSaga.CONCLUIDA, saga.getSituacao());
        assertEquals(EtapaSaga.AGUARDANDO_FINALIZACAO_EXECUCAO, saga.getEtapaAtual());
    }

    @Test
    void ignoraEventosForaDeOrdemEDuplicatas() {
        SagaOrdemServico saga = novaSaga();
        EventoSaga foraDeOrdem =
                new EventoSaga("message-1", "OrcamentoAprovado", AGORA, Map.of("orcamentoId", "orcamento-1"));

        assertFalse(saga.aoReceber(foraDeOrdem, AGORA, PRAZO_PAGAMENTO, PRAZO_COMPENSACAO)
                .aplicada());
        assertFalse(saga.aoReceber(foraDeOrdem, AGORA, PRAZO_PAGAMENTO, PRAZO_COMPENSACAO)
                .aplicada());

        assertEquals(1, saga.getHistorico().size());
        assertFalse(saga.getHistorico().getFirst().isAplicado());
        assertEquals(EtapaSaga.AGUARDANDO_DIAGNOSTICO_INICIADO, saga.getEtapaAtual());
    }

    @Test
    void recusaDeOrcamentoLiberaReservaAntesDeCancelarOs() {
        SagaOrdemServico saga = ateAguardarRespostaOrcamento();

        var compensacao = receber(saga, "OrcamentoRecusado", Map.of("observacao", "Caro"));

        assertEquals(SituacaoSaga.COMPENSANDO, saga.getSituacao());
        assertEquals("LiberarReservaPecas", compensacao.comandos().getFirst().tipo());
        assertEquals(
                "ORCAMENTO_RECUSADO",
                compensacao.comandos().getFirst().payload().get("motivo"));
        assertEquals(
                StatusOrdemServico.CANCELADA,
                receber(saga, "ReservaPecasLiberada", Map.of("reservaId", "reserva-1"))
                        .novoStatus());
        assertEquals(SituacaoSaga.COMPENSADA, saga.getSituacao());
    }

    @Test
    void pagamentoRejeitadoLiberaReservaETimeoutCancelaCobrancaAntes() {
        SagaOrdemServico rejeitada = ateAguardarPagamento();
        var rejeicao = receber(rejeitada, "PagamentoRejeitado", Map.of("motivo", "rejeitado"));
        assertEquals("LiberarReservaPecas", rejeicao.comandos().getFirst().tipo());
        assertEquals(
                StatusOrdemServico.CANCELADA,
                receber(rejeitada, "ReservaPecasLiberada", Map.of("reservaId", "reserva-1"))
                        .novoStatus());

        SagaOrdemServico expirada = ateAguardarPagamento();
        var timeout = expirada.expirarPagamento(expirada.getPrazoExpiracao(), PRAZO_COMPENSACAO);
        assertEquals("CancelarCobranca", timeout.comandos().getFirst().tipo());
        assertEquals(
                "LiberarReservaPecas",
                receber(
                                expirada,
                                "CobrancaCancelada",
                                Map.of("cobrancaId", "cobranca-1", "motivo", "PRAZO_PAGAMENTO_EXPIRADO"))
                        .comandos()
                        .getFirst()
                        .tipo());
        assertEquals(
                StatusOrdemServico.CANCELADA,
                receber(expirada, "ReservaPecasLiberada", Map.of("reservaId", "reserva-1"))
                        .novoStatus());
    }

    @Test
    void cobrancaCriadaAtrasadaAposCompensacaoDisparaNovoCancelamento() {
        SagaOrdemServico saga = ateAguardarPagamento();
        saga.solicitarCancelamento("Desistência", AGORA.plusSeconds(2), PRAZO_COMPENSACAO);
        receber(saga, "CobrancaCancelada", Map.of("cobrancaId", "cobranca-1", "motivo", "CANCELAMENTO"));
        receber(saga, "ReservaPecasLiberada", Map.of("reservaId", "reserva-1"));

        var cobrancaTardia = receber(
                saga,
                "CobrancaCriada",
                Map.of(
                        "cobrancaId",
                        "cobranca-1",
                        "expiraEm",
                        AGORA.plus(PRAZO_PAGAMENTO).toString()));

        assertEquals(SituacaoSaga.COMPENSANDO, saga.getSituacao());
        assertEquals(EtapaSaga.COMPENSANDO_COBRANCA, saga.getEtapaAtual());
        assertEquals("CancelarCobranca", cobrancaTardia.comandos().getFirst().tipo());
        assertEquals(
                StatusOrdemServico.CANCELADA,
                receber(saga, "CobrancaCancelada", Map.of("cobrancaId", "cobranca-1", "motivo", "CANCELAMENTO"))
                        .novoStatus());
        assertEquals(SituacaoSaga.COMPENSADA, saga.getSituacao());
    }

    @Test
    void recusaDeExecucaoEstornaPagamentoAntesDeLiberarReserva() {
        SagaOrdemServico saga = ateAguardarInicioExecucao();

        var estorno = receber(saga, "ExecucaoRecusada", Map.of("reservaId", "reserva-1", "motivo", "recusada"));
        assertEquals("EstornarPagamento", estorno.comandos().getFirst().tipo());
        assertEquals(
                "LiberarReservaPecas",
                receber(saga, "PagamentoEstornado", Map.of("cobrancaId", "cobranca-1", "estornoIdExterno", "estorno-1"))
                        .comandos()
                        .getFirst()
                        .tipo());
        assertEquals(
                StatusOrdemServico.CANCELADA,
                receber(saga, "ReservaPecasLiberada", Map.of("reservaId", "reserva-1"))
                        .novoStatus());
        assertEquals(SituacaoSaga.COMPENSADA, saga.getSituacao());
    }

    @Test
    void cancelamentoManualDuranteExecucaoCancelaTarefaAntesDeEstornarPagamento() {
        SagaOrdemServico saga = ateAguardarInicioExecucao();

        var cancelamento = saga.solicitarCancelamento("Desistência", AGORA.plusSeconds(2), PRAZO_COMPENSACAO);
        assertEquals("CancelarExecucao", cancelamento.comandos().getFirst().tipo());
        assertEquals(
                "EstornarPagamento",
                receber(saga, "ExecucaoCancelada", Map.of("reservaId", "reserva-1", "tarefaId", "tarefa-1"))
                        .comandos()
                        .getFirst()
                        .tipo());
        assertEquals(
                "LiberarReservaPecas",
                receber(saga, "PagamentoEstornado", Map.of("cobrancaId", "cobranca-1", "estornoIdExterno", "estorno-1"))
                        .comandos()
                        .getFirst()
                        .tipo());
        assertEquals(
                StatusOrdemServico.CANCELADA,
                receber(saga, "ReservaPecasLiberada", Map.of("reservaId", "reserva-1"))
                        .novoStatus());
        assertEquals(SituacaoSaga.COMPENSADA, saga.getSituacao());
    }

    @Test
    void finalizacaoQueVenceCorridaComCancelamentoMantemSagaConcluida() {
        SagaOrdemServico saga = ateAguardarInicioExecucao();
        saga.aoReceber(
                new EventoSaga(null, "ExecucaoIniciada", AGORA.plusSeconds(2), Map.of()),
                AGORA.plusSeconds(2),
                PRAZO_PAGAMENTO,
                PRAZO_COMPENSACAO);
        saga.solicitarCancelamento("Desistência", AGORA.plusSeconds(3), PRAZO_COMPENSACAO);

        var finalizacao = receber(saga, "ExecucaoFinalizada", Map.of());

        assertEquals(StatusOrdemServico.FINALIZADA, finalizacao.novoStatus());
        assertEquals(SituacaoSaga.CONCLUIDA, saga.getSituacao());
    }

    @Test
    void falhaCompensacaoFicaVisivelDepoisDoLimiteDeTentativas() {
        SagaOrdemServico saga = ateAguardarRespostaOrcamento();
        receber(saga, "OrcamentoRecusado", Map.of());

        var falha = saga.repetirCompensacao(AGORA.plus(PRAZO_COMPENSACAO).plusSeconds(1), PRAZO_COMPENSACAO, 1);

        assertTrue(falha.aplicada());
        assertTrue(falha.comandos().isEmpty());
        assertEquals(SituacaoSaga.FALHA_COMPENSACAO, saga.getSituacao());
        assertTrue(saga.getFalhaCompensacao().contains("1 tentativas"));
    }

    @Test
    void inicioEEventosDuplicadosSaoIdempotentesEEntradaInvalidaFalhaExplicitamente() {
        SagaOrdemServico saga = novaSaga();

        assertTrue(saga.iniciar(AGORA).aplicada());
        assertFalse(saga.iniciar(AGORA.plusSeconds(1)).aplicada());
        EventoSaga eventoDuplicado = new EventoSaga("message-duplicada", "DiagnosticoIniciado", AGORA, Map.of());
        assertTrue(saga.aoReceber(eventoDuplicado, AGORA, PRAZO_PAGAMENTO, PRAZO_COMPENSACAO)
                .aplicada());
        assertFalse(saga.aoReceber(eventoDuplicado, AGORA, PRAZO_PAGAMENTO, PRAZO_COMPENSACAO)
                .aplicada());
        assertEquals(
                1,
                saga.getHistorico().stream()
                        .filter(e -> "DiagnosticoIniciado".equals(e.getTipo()))
                        .count());

        assertThrows(
                IllegalArgumentException.class, () -> saga.aoReceber(null, AGORA, PRAZO_PAGAMENTO, PRAZO_COMPENSACAO));
        SagaOrdemServico incompleta = novaSaga();
        incompleta.iniciar(AGORA);
        receber(incompleta, "DiagnosticoIniciado", Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> receber(incompleta, "DiagnosticoConcluido", Map.of("valorTotal", BigDecimal.ONE)));
    }

    @Test
    void retriesDeCompensacaoReemitemComandoAteOLimite() {
        SagaOrdemServico saga = ateAguardarRespostaOrcamento();
        receber(saga, "OrcamentoRecusado", Map.of());
        Instant primeiraExpiracao = saga.getPrazoExpiracao();

        var retry = saga.repetirCompensacao(primeiraExpiracao, PRAZO_COMPENSACAO, 3);

        assertTrue(retry.aplicada());
        assertEquals(2, saga.getTentativas());
        assertEquals("LiberarReservaPecas", retry.comandos().getFirst().tipo());
        assertEquals(primeiraExpiracao.plus(PRAZO_COMPENSACAO), saga.getPrazoExpiracao());
        assertFalse(saga.repetirCompensacao(primeiraExpiracao.plusSeconds(1), PRAZO_COMPENSACAO, 3)
                .aplicada());
    }

    @Test
    void cancelamentoNoInicioLiberaReservaSemEsperarParticipanteInexistente() {
        SagaOrdemServico saga = novaSaga();
        var cancelamento = saga.solicitarCancelamento(null, AGORA.plusSeconds(1), PRAZO_COMPENSACAO);

        assertEquals("LiberarReservaPecas", cancelamento.comandos().getFirst().tipo());
        assertEquals(
                "CANCELAMENTO", cancelamento.comandos().getFirst().payload().get("motivo"));
        var liberada = receber(saga, "ReservaPecasLiberada", Map.of());

        assertEquals(StatusOrdemServico.CANCELADA, liberada.novoStatus());
        assertEquals(SituacaoSaga.COMPENSADA, saga.getSituacao());
        assertFalse(saga.solicitarCancelamento("repetido", AGORA.plusSeconds(2), PRAZO_COMPENSACAO)
                .aplicada());
    }

    @Test
    void eventoDeCobrancaCanceladaOuEstornoComIdsDivergentesNaoConcluiCompensacao() {
        SagaOrdemServico saga = ateAguardarPagamento();
        saga.expirarPagamento(saga.getPrazoExpiracao(), PRAZO_COMPENSACAO);

        var divergente =
                receber(saga, "CobrancaCancelada", Map.of("cobrancaId", "cobranca-errada", "motivo", "cancelada"));

        assertFalse(divergente.aplicada());
        assertEquals(SituacaoSaga.COMPENSANDO, saga.getSituacao());
        assertEquals(EtapaSaga.COMPENSANDO_COBRANCA, saga.getEtapaAtual());
    }

    /**
     * Com o gateway aprovando na criação da cobrança, o Billing publica {@code CobrancaCriada} e
     * {@code PagamentoConfirmado} juntos; com várias réplicas consumindo a fila, o pagamento pode ser processado
     * primeiro. Ele não pode ser descartado: a Saga segue para a execução e ignora a cobrança que chega depois.
     */
    @Test
    void pagamentoConfirmadoAntesDaCobrancaCriadaSegueParaAExecucao() {
        SagaOrdemServico saga = ateAguardarRespostaOrcamento();
        receber(saga, "OrcamentoAprovado", Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")));

        DecisaoSaga pagamento = receber(
                saga, "PagamentoConfirmado", Map.of("cobrancaId", "cobranca-1", "pagamentoIdExterno", "pagamento-1"));

        assertTrue(pagamento.aplicada());
        assertEquals(StatusOrdemServico.AGUARDANDO_PAGAMENTO, pagamento.novoStatus());
        assertEquals("IniciarExecucao", pagamento.comandos().getFirst().tipo());
        assertEquals("cobranca-1", pagamento.comandos().getFirst().payload().get("cobrancaId"));
        assertEquals("reserva-1", pagamento.comandos().getFirst().payload().get("reservaId"));
        assertEquals(EtapaSaga.AGUARDANDO_INICIO_EXECUCAO, saga.getEtapaAtual());
        assertEquals("cobranca-1", saga.getCobrancaId());
        assertNull(saga.getPrazoExpiracao());

        DecisaoSaga cobrancaAtrasada = receber(
                saga,
                "CobrancaCriada",
                Map.of(
                        "cobrancaId",
                        "cobranca-1",
                        "expiraEm",
                        AGORA.plus(PRAZO_PAGAMENTO).toString()));

        assertFalse(cobrancaAtrasada.aplicada());
        assertEquals(EtapaSaga.AGUARDANDO_INICIO_EXECUCAO, saga.getEtapaAtual());
        assertNull(saga.getPrazoExpiracao());
        assertEquals(
                StatusOrdemServico.EM_EXECUCAO,
                receber(saga, "ExecucaoIniciada", Map.of()).novoStatus());
    }

    @Test
    void pagamentoRejeitadoAntesDaCobrancaCriadaLiberaAReserva() {
        SagaOrdemServico saga = ateAguardarRespostaOrcamento();
        receber(saga, "OrcamentoAprovado", Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")));

        DecisaoSaga rejeicao = receber(
                saga, "PagamentoRejeitado", Map.of("cobrancaId", "cobranca-1", "motivo", "cc_rejected_other_reason"));

        assertTrue(rejeicao.aplicada());
        assertEquals("LiberarReservaPecas", rejeicao.comandos().getFirst().tipo());
        assertEquals(SituacaoSaga.COMPENSANDO, saga.getSituacao());
        assertEquals(MotivoCompensacao.PAGAMENTO_REJEITADO, saga.getMotivoCompensacao());

        assertFalse(receber(
                        saga,
                        "CobrancaCriada",
                        Map.of(
                                "cobrancaId",
                                "cobranca-1",
                                "expiraEm",
                                AGORA.plus(PRAZO_PAGAMENTO).toString()))
                .aplicada());
        assertEquals(
                StatusOrdemServico.CANCELADA,
                receber(saga, "ReservaPecasLiberada", Map.of("reservaId", "reserva-1"))
                        .novoStatus());
        assertEquals(SituacaoSaga.COMPENSADA, saga.getSituacao());
    }

    private SagaOrdemServico ateAguardarRespostaOrcamento() {
        SagaOrdemServico saga = novaSaga();
        saga.iniciar(AGORA);
        receber(saga, "DiagnosticoIniciado", Map.of());
        receber(saga, "DiagnosticoConcluido", Map.of("reservaId", "reserva-1", "valorTotal", new BigDecimal("25.00")));
        receber(saga, "OrcamentoGerado", Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")));
        return saga;
    }

    private SagaOrdemServico ateAguardarPagamento() {
        SagaOrdemServico saga = ateAguardarRespostaOrcamento();
        receber(saga, "OrcamentoAprovado", Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")));
        receber(
                saga,
                "CobrancaCriada",
                Map.of(
                        "cobrancaId",
                        "cobranca-1",
                        "expiraEm",
                        AGORA.plus(PRAZO_PAGAMENTO).toString()));
        return saga;
    }

    private SagaOrdemServico ateAguardarInicioExecucao() {
        SagaOrdemServico saga = ateAguardarPagamento();
        receber(saga, "PagamentoConfirmado", Map.of("cobrancaId", "cobranca-1", "pagamentoIdExterno", "pagamento-1"));
        return saga;
    }

    private SagaOrdemServico novaSaga() {
        return new SagaOrdemServico("os-1", AGORA);
    }

    private DecisaoSaga receber(SagaOrdemServico saga, String tipo, Map<String, Object> payload) {
        return saga.aoReceber(
                new EventoSaga(null, tipo, AGORA.plusSeconds(1), payload),
                AGORA.plusSeconds(1),
                PRAZO_PAGAMENTO,
                PRAZO_COMPENSACAO);
    }
}
