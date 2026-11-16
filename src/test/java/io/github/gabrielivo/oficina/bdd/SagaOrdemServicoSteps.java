package io.github.gabrielivo.oficina.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.EventoSaga;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.SituacaoSaga;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

class SagaOrdemServicoSteps {

    private static final Instant INICIO = Instant.parse("2026-11-16T12:00:00Z");
    private static final Duration PRAZO_PAGAMENTO = Duration.ofMinutes(30);
    private static final Duration PRAZO_COMPENSACAO = Duration.ofSeconds(30);

    private OrdemServico ordemServico;
    private SagaOrdemServico saga;
    private Instant agora;

    @Before
    void iniciarCenario() {
        Cliente cliente = new Cliente("52998224725", "Cliente BDD", null, "bdd@example.com", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1D23", "Fiat", "Uno", 2020);
        ordemServico = new OrdemServico(cliente, veiculo);
        saga = new SagaOrdemServico(ordemServico.getId(), INICIO);
        agora = INICIO;
    }

    @Dado("que uma OS foi aberta")
    public void queUmaOsFoiAberta() {
        assertEquals(
                "SolicitarDiagnostico",
                saga.iniciar(agora).comandos().getFirst().tipo());
    }

    @Quando("o diagnóstico é concluído e o orçamento é gerado")
    public void diagnosticoConcluidoEOrcamentoGerado() {
        receber("DiagnosticoIniciado", Map.of());
        receber("DiagnosticoConcluido", Map.of("reservaId", "reserva-1", "valorTotal", new BigDecimal("25.00")));
        receber("OrcamentoGerado", Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")));
    }

    @Quando("o orçamento é aprovado")
    public void orcamentoAprovado() {
        assertEquals(
                "CriarCobranca",
                receber(
                                "OrcamentoAprovado",
                                Map.of("orcamentoId", "orcamento-1", "valorTotal", new BigDecimal("25.00")))
                        .comandos()
                        .getFirst()
                        .tipo());
    }

    @Quando("a cobrança é criada, paga e a execução é finalizada")
    public void cobrancaPagaEExecucaoFinalizada() {
        receber(
                "CobrancaCriada",
                Map.of(
                        "cobrancaId",
                        "cobranca-1",
                        "expiraEm",
                        agora.plus(PRAZO_PAGAMENTO).toString()));
        assertEquals(
                "IniciarExecucao",
                receber("PagamentoConfirmado", Map.of("cobrancaId", "cobranca-1", "pagamentoIdExterno", "pagamento-1"))
                        .comandos()
                        .getFirst()
                        .tipo());
        receber("ExecucaoIniciada", Map.of());
        receber("ExecucaoFinalizada", Map.of());
    }

    @Quando("a OS é entregue ao cliente")
    public void osEntregueAoCliente() {
        ordemServico.entregar();
    }

    @Quando("o orçamento é recusado")
    public void orcamentoRecusado() {
        assertEquals(
                "LiberarReservaPecas",
                receber("OrcamentoRecusado", Map.of()).comandos().getFirst().tipo());
    }

    @Quando("a reserva de peças é liberada")
    public void reservaDePecasLiberada() {
        receber("ReservaPecasLiberada", Map.of("reservaId", "reserva-1"));
    }

    @Quando("a cobrança aguarda pagamento")
    public void cobrancaAguardaPagamento() {
        diagnosticoConcluidoEOrcamentoGerado();
        orcamentoAprovado();
        receber(
                "CobrancaCriada",
                Map.of(
                        "cobrancaId",
                        "cobranca-1",
                        "expiraEm",
                        agora.plus(PRAZO_PAGAMENTO).toString()));
    }

    @Quando("o prazo do pagamento expira")
    public void prazoPagamentoExpira() {
        agora = saga.getPrazoExpiracao().plusNanos(1);
        assertEquals(
                "CancelarCobranca",
                saga.expirarPagamento(agora, PRAZO_COMPENSACAO)
                        .comandos()
                        .getFirst()
                        .tipo());
    }

    @Quando("a cobrança é cancelada")
    public void cobrancaCancelada() {
        receber("CobrancaCancelada", Map.of("cobrancaId", "cobranca-1", "motivo", "PRAZO_PAGAMENTO_EXPIRADO"));
    }

    @Quando("o pagamento é confirmado")
    public void pagamentoConfirmado() {
        assertEquals(
                "IniciarExecucao",
                receber("PagamentoConfirmado", Map.of("cobrancaId", "cobranca-1", "pagamentoIdExterno", "pagamento-1"))
                        .comandos()
                        .getFirst()
                        .tipo());
    }

    @Quando("a execução é recusada")
    public void execucaoRecusada() {
        assertEquals(
                "EstornarPagamento",
                receber("ExecucaoRecusada", Map.of("reservaId", "reserva-1", "motivo", "falha simulada"))
                        .comandos()
                        .getFirst()
                        .tipo());
    }

    @Quando("o pagamento é estornado")
    public void pagamentoEstornado() {
        assertEquals(
                "LiberarReservaPecas",
                receber("PagamentoEstornado", Map.of("cobrancaId", "cobranca-1", "estornoIdExterno", "estorno-1"))
                        .comandos()
                        .getFirst()
                        .tipo());
    }

    @Entao("a OS fica no status {string}")
    public void osFicaNoStatus(String status) {
        assertEquals(StatusOrdemServico.valueOf(status), ordemServico.getStatus());
    }

    @Entao("a Saga fica na situação {string}")
    public void sagaFicaNaSituacao(String situacao) {
        assertEquals(SituacaoSaga.valueOf(situacao), saga.getSituacao());
    }

    private io.github.gabrielivo.oficina.domain.saga.DecisaoSaga receber(String tipo, Map<String, Object> payload) {
        agora = agora.plusSeconds(1);
        var decisao = saga.aoReceber(
                new EventoSaga(UUID.randomUUID().toString(), tipo, agora, payload),
                agora,
                PRAZO_PAGAMENTO,
                PRAZO_COMPENSACAO);
        assertTrue(decisao.aplicada(), "Evento " + tipo + " deveria ser aplicado.");
        if (decisao.novoStatus() != null) {
            ordemServico.alterarStatus(decisao.novoStatus(), OrigemMudancaStatus.SAGA, tipo);
        }
        return decisao;
    }
}
