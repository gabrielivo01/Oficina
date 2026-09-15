package io.github.gabrielivo.oficina.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";

    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${app.internal.api-key:}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        if (securityEnabled && request.getRequestURI().startsWith("/internal/")) {
            String providedKey = request.getHeader(HEADER);
            if (providedKey == null || !providedKey.equals(expectedApiKey)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Chave de API interna inválida.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
