package io.github.gabrielivo.oficina.saga.memoria;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Billing Service falso. Reage sozinho a {@code GerarOrcamento}, {@code CriarCobranca}, {@code CancelarCobranca} e
 * {@code EstornarPagamento}; a resposta do cliente ao orçamento e o resultado do pagamento (que no serviço real vêm da
 * API e do gateway) são disparados pelo teste.
 */
public final class BillingFalso extends ParticipanteFalso {

    private final Map<String, String> orcamentos = new HashMap<>();
    private final Map<String, String> cobrancas = new HashMap<>();
    private Duration prazoPagamento = Duration.ofMinutes(30);

    public BillingFalso(CatalogoContratos catalogo, Consumer<EnvelopeMensagem> publicarEvento) {
        super(catalogo, publicarEvento);
    }

    @Override
    public String fila() {
        return "billing.comandos";
    }

    /** Prazo usado no {@code expiraEm} das próximas cobranças ({@code Duration.ZERO} = vencem ao serem criadas). */
    public void prazoPagamento(Duration prazo) {
        this.prazoPagamento = prazo;
    }

    public void responderOrcamento(String osId, boolean aprovado) {
        exigirComando(osId, "GerarOrcamento");
        String tipo = aprovado ? "OrcamentoAprovado" : "OrcamentoRecusado";
        publicar(catalogo.evento(tipo, osId, Map.of("orcamentoId", orcamento(osId))));
    }

    public void confirmarPagamento(String osId) {
        exigirComando(osId, "CriarCobranca");
        publicar(catalogo.evento("PagamentoConfirmado", osId, Map.of("cobrancaId", cobranca(osId))));
    }

    public void rejeitarPagamento(String osId) {
        exigirComando(osId, "CriarCobranca");
        publicar(catalogo.evento("PagamentoRejeitado", osId, Map.of("cobrancaId", cobranca(osId))));
    }

    @Override
    protected void reagir(EnvelopeMensagem comando) {
        String osId = comando.osId();
        switch (comando.type()) {
            case "GerarOrcamento" ->
                publicar(catalogo.evento("OrcamentoGerado", osId, Map.of("orcamentoId", orcamento(osId))));
            case "CriarCobranca" ->
                publicar(catalogo.evento(
                        "CobrancaCriada",
                        osId,
                        Map.of(
                                "cobrancaId",
                                cobranca(osId),
                                "orcamentoId",
                                orcamento(osId),
                                "expiraEm",
                                Instant.now().plus(prazoPagamento).toString())));
            case "CancelarCobranca" ->
                publicar(catalogo.evento("CobrancaCancelada", osId, Map.of("cobrancaId", cobranca(osId))));
            case "EstornarPagamento" ->
                publicar(catalogo.evento("PagamentoEstornado", osId, Map.of("cobrancaId", cobranca(osId))));
            default -> throw new IllegalArgumentException("Comando inesperado no Billing: " + comando.type());
        }
    }

    private void exigirComando(String osId, String tipo) {
        if (!recebeu(osId, tipo)) {
            throw new IllegalStateException("O Billing não recebeu " + tipo + " para a OS " + osId);
        }
    }

    private String orcamento(String osId) {
        return orcamentos.computeIfAbsent(osId, id -> UUID.randomUUID().toString());
    }

    private String cobranca(String osId) {
        return cobrancas.computeIfAbsent(osId, id -> UUID.randomUUID().toString());
    }
}
