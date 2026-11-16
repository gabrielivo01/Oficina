package io.github.gabrielivo.oficina.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Tracer> provedor(Tracer tracer) {
        ObjectProvider<Tracer> provedor = mock(ObjectProvider.class);
        when(provedor.getIfAvailable()).thenReturn(tracer);
        return provedor;
    }

    private static Span spanComTraceId() {
        Span span = mock(Span.class);
        TraceContext contexto = mock(TraceContext.class);
        when(span.context()).thenReturn(contexto);
        when(contexto.traceId()).thenReturn(TRACE_ID);
        return span;
    }

    private static String executar(
            CorrelationIdFilter filtro, MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        AtomicReference<String> mdcDuranteARequisicao = new AtomicReference<>();
        filtro.doFilterInternal(request, response, (req, res) -> mdcDuranteARequisicao.set(MDC.get("correlationId")));
        assertThat(MDC.get("correlationId")).as("MDC limpo ao final").isNull();
        return mdcDuranteARequisicao.get();
    }

    @Test
    void deveUsarOTraceIdComoCorrelationIdQuandoNaoInformado() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = spanComTraceId();
        when(tracer.currentSpan()).thenReturn(span);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String noMdc = executar(new CorrelationIdFilter(provedor(tracer)), new MockHttpServletRequest(), response);

        assertThat(noMdc).isEqualTo(TRACE_ID);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(TRACE_ID);
        verify(span, never()).tag(anyString(), anyString());
    }

    @Test
    void deveManterCorrelationIdRecebidoEMarcarNoSpan() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = spanComTraceId();
        when(tracer.currentSpan()).thenReturn(span);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "id-do-chamador");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String noMdc = executar(new CorrelationIdFilter(provedor(tracer)), request, response);

        assertThat(noMdc).isEqualTo("id-do-chamador");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("id-do-chamador");
        verify(span).tag("correlation.id", "id-do-chamador");
    }

    @Test
    void deveGerarUuidQuandoNaoHaTracing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String noMdc = executar(new CorrelationIdFilter(provedor(null)), new MockHttpServletRequest(), response);

        assertThat(noMdc).hasSize(36).isEqualTo(response.getHeader("X-Correlation-Id"));
    }

    @Test
    void deveGerarUuidQuandoNaoHaSpanCorrente() throws Exception {
        Tracer tracer = mock(Tracer.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String noMdc = executar(new CorrelationIdFilter(provedor(tracer)), new MockHttpServletRequest(), response);

        assertThat(noMdc).hasSize(36);
    }
}
