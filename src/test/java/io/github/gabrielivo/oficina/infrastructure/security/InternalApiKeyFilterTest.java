package io.github.gabrielivo.oficina.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class InternalApiKeyFilterTest {

    private InternalApiKeyFilter criarFiltro(boolean securityEnabled, String apiKey) {
        InternalApiKeyFilter filtro = new InternalApiKeyFilter();
        ReflectionTestUtils.setField(filtro, "securityEnabled", securityEnabled);
        ReflectionTestUtils.setField(filtro, "expectedApiKey", apiKey);
        return filtro;
    }

    @Test
    void deveBloquearRequisicaoInternaSemChaveQuandoSegurancaHabilitada() throws ServletException, IOException {
        InternalApiKeyFilter filtro = criarFiltro(true, "chave-correta");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/clientes/11122233344/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        filtro.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void deveBloquearRequisicaoInternaComChaveErradaQuandoSegurancaHabilitada() throws ServletException, IOException {
        InternalApiKeyFilter filtro = criarFiltro(true, "chave-correta");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/clientes/11122233344/status");
        request.addHeader("X-Internal-Api-Key", "chave-errada");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        filtro.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void devePermitirRequisicaoInternaComChaveCorretaQuandoSegurancaHabilitada() throws ServletException, IOException {
        InternalApiKeyFilter filtro = criarFiltro(true, "chave-correta");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/clientes/11122233344/status");
        request.addHeader("X-Internal-Api-Key", "chave-correta");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        filtro.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void devePermitirRequisicaoInternaSemChaveQuandoSegurancaDesabilitada() throws ServletException, IOException {
        InternalApiKeyFilter filtro = criarFiltro(false, "chave-correta");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/clientes/11122233344/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        filtro.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void devePermitirRequisicaoNaoInternaSemChave() throws ServletException, IOException {
        InternalApiKeyFilter filtro = criarFiltro(true, "chave-correta");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clientes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        filtro.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }
}
