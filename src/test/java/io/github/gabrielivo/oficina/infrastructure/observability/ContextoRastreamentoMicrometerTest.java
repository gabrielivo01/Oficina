package io.github.gabrielivo.oficina.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ContextoRastreamentoMicrometerTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    @SuppressWarnings("unchecked")
    void deveDevolverOTraceparentDoSpanCorrente() {
        Tracer tracer = mock(Tracer.class);
        Propagator propagator = mock(Propagator.class);
        Span span = mock(Span.class);
        TraceContext contexto = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(contexto);
        doAnswer(invocacao -> {
                    Propagator.Setter<Object> setter = invocacao.getArgument(2);
                    setter.set(invocacao.getArgument(1), "traceparent", TRACEPARENT);
                    return null;
                })
                .when(propagator)
                .inject(eq(contexto), any(), any(Propagator.Setter.class));

        assertThat(new ContextoRastreamentoMicrometer(tracer, propagator).traceparentAtual())
                .isEqualTo(TRACEPARENT);
    }

    @Test
    void deveDevolverNuloSemTraceAtivo() {
        Tracer tracer = mock(Tracer.class);

        assertThat(new ContextoRastreamentoMicrometer(tracer, mock(Propagator.class)).traceparentAtual())
                .isNull();
    }

    @Test
    void naoDeveObservarRequisicoesDoActuatorMasSimAsDaApi() {
        ObservationPredicate predicado = new ObservabilidadeConfig().ignorarRequisicoesDoActuator();

        assertThat(predicado.test("http.server.requests", contextoHttp("/actuator/prometheus")))
                .isFalse();
        assertThat(predicado.test("http.server.requests", contextoHttp("/ordens-servico")))
                .isTrue();
        assertThat(predicado.test("spring.rabbit.listener", new Observation.Context()))
                .isTrue();
    }

    private static ServerRequestObservationContext contextoHttp(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        return new ServerRequestObservationContext(request, new MockHttpServletResponse());
    }
}
