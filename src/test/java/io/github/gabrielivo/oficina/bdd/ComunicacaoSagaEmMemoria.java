package io.github.gabrielivo.oficina.bdd;

import io.github.gabrielivo.oficina.saga.memoria.SagaEmMemoria;
import java.time.Duration;
import java.util.List;

/** Comunicação dos cenários BDD pela Saga em memória (Etapa 5.4): roda no {@code verify}, sem Docker. */
class ComunicacaoSagaEmMemoria implements ComunicacaoSaga {

    private final SagaEmMemoria saga = new SagaEmMemoria();

    @Override
    public void recusarInicioDaExecucao() {
        saga.execution().recusarInicioExecucao();
    }

    @Override
    public void cobrancasVencendoAoSerCriadas() {
        saga.billing().prazoPagamento(Duration.ZERO);
    }

    @Override
    public String abrirOrdemServico() {
        return saga.abrirOrdemServico();
    }

    @Override
    public void concluirDiagnostico(String osId) {
        saga.concluirDiagnostico(osId);
    }

    @Override
    public void responderOrcamento(String osId, boolean aprovado) {
        saga.responderOrcamento(osId, aprovado);
    }

    @Override
    public void pagarCobranca(String osId) {
        saga.confirmarPagamento(osId);
    }

    @Override
    public void verificarPrazosDaSaga() {
        saga.verificarPrazos();
    }

    @Override
    public void finalizarExecucao(String osId) {
        saga.finalizarExecucao(osId);
    }

    @Override
    public void entregar(String osId) {
        saga.entregar(osId);
    }

    @Override
    public String statusDaOrdem(String osId) {
        return saga.status(osId).name();
    }

    @Override
    public String situacaoDaSaga(String osId) {
        return saga.situacao(osId).name();
    }

    @Override
    public List<String> comandosRecebidos(String servico, String osId) {
        return switch (servico) {
            case "Execution" -> saga.execution().comandosRecebidos(osId);
            case "Billing" -> saga.billing().comandosRecebidos(osId);
            default -> throw new IllegalArgumentException("Serviço desconhecido: " + servico);
        };
    }
}
