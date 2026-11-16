package io.github.gabrielivo.oficina.infrastructure.observability;

import io.github.gabrielivo.oficina.application.mensageria.ContextoRastreamento;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Lê o contexto de trace corrente (Micrometer Tracing + OpenTelemetry) no formato W3C. */
@Component
public class ContextoRastreamentoMicrometer implements ContextoRastreamento {

    public static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public ContextoRastreamentoMicrometer(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public String traceparentAtual() {
        Span atual = tracer.currentSpan();
        if (atual == null) {
            return null;
        }
        Map<String, String> cabecalhos = new HashMap<>();
        propagator.inject(atual.context(), cabecalhos, Map::put);
        return cabecalhos.get(TRACEPARENT);
    }
}
