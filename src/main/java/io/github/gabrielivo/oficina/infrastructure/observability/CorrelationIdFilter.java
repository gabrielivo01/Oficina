package io.github.gabrielivo.oficina.infrastructure.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Id de correlação por requisição (header {@code X-Correlation-Id} + MDC {@code correlationId}), integrado ao tracing:
 * sem header de entrada, o id de correlação <b>é o traceId</b> da requisição (o mesmo que aparece no Jaeger e nos
 * campos {@code traceId}/{@code spanId} do log), em vez de um UUID paralelo. Se o chamador enviar um id, ele é mantido
 * e gravado no span como {@code correlation.id}, para achar o trace a partir dele.
 *
 * <p>Roda depois do filtro de observação HTTP (ordem padrão), quando o span da requisição já existe.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    static final String TAG_SPAN = "correlation.id";

    private final ObjectProvider<Tracer> tracer;

    public CorrelationIdFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Span span = spanAtual();
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId =
                    span != null ? span.context().traceId() : UUID.randomUUID().toString();
        } else if (span != null) {
            span.tag(TAG_SPAN, correlationId);
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private Span spanAtual() {
        Tracer atual = tracer.getIfAvailable();
        return atual == null ? null : atual.currentSpan();
    }
}
