package io.github.gabrielivo.oficina.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";

    private final boolean securityEnabled;
    private final byte[] expectedApiKey;

    public InternalApiKeyFilter(SegurancaProperties seguranca, InternalApiProperties internalApi) {
        this.securityEnabled = seguranca.enabled();
        String chave = internalApi.apiKey();
        this.expectedApiKey = chave == null || chave.isBlank() ? null : chave.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (securityEnabled && request.getRequestURI().startsWith("/internal/") && !chaveValida(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Chave de API interna inválida.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Sem chave configurada, nada passa. A comparação é em tempo constante (não vaza o prefixo correto). */
    private boolean chaveValida(HttpServletRequest request) {
        String providedKey = request.getHeader(HEADER);
        if (expectedApiKey == null || providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(providedKey.getBytes(StandardCharsets.UTF_8), expectedApiKey);
    }
}
