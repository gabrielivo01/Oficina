package io.github.gabrielivo.oficina.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;

/**
 * Steps da Saga. Não conhecem a forma de comunicação: tudo passa por {@link ComunicacaoSaga}, para o Plano 2 rodar os
 * mesmos cenários contra os serviços reais.
 */
public class SagaOrdemServicoSteps {

    private ComunicacaoSaga comunicacao;
    private String osId;

    @Before
    public void iniciarCenario() {
        comunicacao = ComunicacaoSaga.daConfiguracao();
    }

    @Dado("que o Execution Service recusa o início da execução")
    public void queOExecutionRecusaOInicioDaExecucao() {
        comunicacao.recusarInicioDaExecucao();
    }

    @Dado("que as cobranças vencem assim que são criadas")
    public void queAsCobrancasVencemAssimQueSaoCriadas() {
        comunicacao.cobrancasVencendoAoSerCriadas();
    }

    @Dado("que o cliente abriu uma OS")
    public void queOClienteAbriuUmaOs() {
        osId = comunicacao.abrirOrdemServico();
        assertThat(comunicacao.statusDaOrdem(osId)).isEqualTo("RECEBIDA");
    }

    @Quando("o mecânico conclui o diagnóstico")
    public void oMecanicoConcluiODiagnostico() {
        comunicacao.concluirDiagnostico(osId);
        assertThat(comunicacao.statusDaOrdem(osId)).isEqualTo("AGUARDANDO_APROVACAO");
    }

    @Quando("o cliente aprova o orçamento")
    public void oClienteAprovaOOrcamento() {
        comunicacao.responderOrcamento(osId, true);
        assertThat(comunicacao.statusDaOrdem(osId)).isEqualTo("AGUARDANDO_PAGAMENTO");
    }

    @Quando("o cliente recusa o orçamento")
    public void oClienteRecusaOOrcamento() {
        comunicacao.responderOrcamento(osId, false);
    }

    @Quando("o cliente paga a cobrança")
    public void oClientePagaACobranca() {
        comunicacao.pagarCobranca(osId);
    }

    @Quando("o prazo de pagamento expira sem confirmação")
    public void oPrazoDePagamentoExpiraSemConfirmacao() {
        comunicacao.verificarPrazosDaSaga();
    }

    @Quando("o mecânico finaliza a execução")
    public void oMecanicoFinalizaAExecucao() {
        assertThat(comunicacao.statusDaOrdem(osId)).isEqualTo("EM_EXECUCAO");
        comunicacao.finalizarExecucao(osId);
    }

    @Quando("a OS é entregue ao cliente")
    public void aOsEEntregueAoCliente() {
        comunicacao.entregar(osId);
    }

    @Entao("o {word} Service recebe o comando {string}")
    public void oServicoRecebeOComando(String servico, String comando) {
        assertThat(comunicacao.comandosRecebidos(servico, osId)).contains(comando);
    }

    @Entao("a OS fica no status {string}")
    public void aOsFicaNoStatus(String status) {
        assertThat(comunicacao.statusDaOrdem(osId)).isEqualTo(status);
    }

    @Entao("a Saga fica na situação {string}")
    public void aSagaFicaNaSituacao(String situacao) {
        assertThat(comunicacao.situacaoDaSaga(osId)).isEqualTo(situacao);
    }
}
