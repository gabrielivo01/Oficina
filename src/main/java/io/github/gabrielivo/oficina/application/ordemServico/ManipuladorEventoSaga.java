package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.ManipuladorMensagem;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ManipuladorEventoSaga implements ManipuladorMensagem {

    private static final List<String> TIPOS = List.of(
            "DiagnosticoIniciado",
            "DiagnosticoConcluido",
            "OrcamentoGerado",
            "OrcamentoAprovado",
            "OrcamentoRecusado",
            "CobrancaCriada",
            "CobrancaCancelada",
            "PagamentoConfirmado",
            "PagamentoRejeitado",
            "ExecucaoIniciada",
            "ExecucaoRecusada",
            "ExecucaoCancelada",
            "ExecucaoFinalizada",
            "PagamentoEstornado",
            "ReservaPecasLiberada");

    private final OrquestradorSagaService orquestrador;

    public ManipuladorEventoSaga(OrquestradorSagaService orquestrador) {
        this.orquestrador = orquestrador;
    }

    @Override
    public String tipo() {
        return TIPOS.getFirst();
    }

    @Override
    public List<String> tipos() {
        return TIPOS;
    }

    @Override
    public void manipular(EnvelopeMensagem envelope) {
        orquestrador.processar(envelope);
    }
}
