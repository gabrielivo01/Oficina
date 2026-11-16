package io.github.gabrielivo.oficina.bdd;

import java.util.List;

/**
 * "Forma de comunicação" dos cenários BDD da Saga: os steps falam só com esta interface, em termos do negócio. A
 * implementação em memória ({@link ComunicacaoSagaEmMemoria}) roda no {@code verify}; o Plano 2 acrescenta uma
 * implementação que fala com os três serviços reais (REST do OS, endpoints do mecânico, Billing com gateway fake),
 * reaproveitando o mesmo {@code .feature}.
 *
 * <p>A implementação é escolhida pela propriedade de sistema {@code saga.comunicacao} (padrão {@code memoria}).
 */
public interface ComunicacaoSaga {

    String PROPRIEDADE = "saga.comunicacao";

    static ComunicacaoSaga daConfiguracao() {
        String tipo = System.getProperty(PROPRIEDADE, "memoria");
        if ("memoria".equals(tipo)) {
            return new ComunicacaoSagaEmMemoria();
        }
        throw new IllegalArgumentException("Forma de comunicação da Saga desconhecida: " + tipo
                + ". Disponível: memoria (a implementação contra os serviços reais é do Plano 2).");
    }

    /** Execution passa a recusar o início da execução ({@code app.simulacao.recusar-inicio-execucao=true}). */
    void recusarInicioDaExecucao();

    /** As próximas cobranças vencem assim que são criadas. */
    void cobrancasVencendoAoSerCriadas();

    /** Cadastra cliente e veículo e abre uma OS. @return id da OS */
    String abrirOrdemServico();

    void concluirDiagnostico(String osId);

    void responderOrcamento(String osId, boolean aprovado);

    void pagarCobranca(String osId);

    /** Espera (ou força) a verificação de prazos da Saga. */
    void verificarPrazosDaSaga();

    void finalizarExecucao(String osId);

    void entregar(String osId);

    String statusDaOrdem(String osId);

    String situacaoDaSaga(String osId);

    /** @param servico {@code Execution} ou {@code Billing} */
    List<String> comandosRecebidos(String servico, String osId);
}
