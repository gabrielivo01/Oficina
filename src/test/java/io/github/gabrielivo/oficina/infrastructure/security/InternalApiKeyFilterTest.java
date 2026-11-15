package io.github.gabrielivo.oficina.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiKeyFilterTest {

    private static final String URI_INTERNA = "/internal/clientes/11122233344/status";

    private InternalApiKeyFilter criarFiltro(boolean securityEnabled, String apiKey) {
        return new InternalApiKeyFilter(new SegurancaProperties(securityEnabled), new InternalApiProperties(apiKey));
    }

    private int executar(InternalApiKeyFilter filtro, String uri, String chaveEnviada)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (chaveEnviada != null) {
            request.addHeader("X-Internal-Api-Key", chaveEnviada);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();
        filtro.doFilterInternal(request, response, filterChain);
        return response.getStatus();
    }

    @Test
    void deveBloquearRequisicaoInternaSemChaveQuandoSegurancaHabilitada() throws ServletException, IOException {
        assertEquals(401, executar(criarFiltro(true, "chave-correta"), URI_INTERNA, null));
    }

    @Test
    void deveBloquearRequisicaoInternaComChaveErradaQuandoSegurancaHabilitada() throws ServletException, IOException {
        assertEquals(401, executar(criarFiltro(true, "chave-correta"), URI_INTERNA, "chave-errada"));
    }

    @Test
    void deveBloquearChaveQueSoCompartilhaOPrefixo() throws ServletException, IOException {
        InternalApiKeyFilter filtro = criarFiltro(true, "chave-correta");

        assertEquals(401, executar(filtro, URI_INTERNA, "chave-correta-mais-longa"));
        assertEquals(401, executar(filtro, URI_INTERNA, "chave-corret"));
    }

    @Test
    void devePermitirRequisicaoInternaComChaveCorretaQuandoSegurancaHabilitada() throws ServletException, IOException {
        assertEquals(200, executar(criarFiltro(true, "chave-correta"), URI_INTERNA, "chave-correta"));
    }

    @Test
    void deveBloquearRequisicaoInternaQuandoChaveEsperadaNaoConfigurada() throws ServletException, IOException {
        // Regressão: com app.internal.api-key vazia, um header vazio era aceito ("".equals("")).
        assertEquals(401, executar(criarFiltro(true, ""), URI_INTERNA, ""));
        assertEquals(401, executar(criarFiltro(true, null), URI_INTERNA, ""));
        assertEquals(401, executar(criarFiltro(true, "   "), URI_INTERNA, "   "));
    }

    @Test
    void devePermitirRequisicaoInternaSemChaveQuandoSegurancaDesabilitada() throws ServletException, IOException {
        assertEquals(200, executar(criarFiltro(false, "chave-correta"), URI_INTERNA, null));
    }

    @Test
    void devePermitirRequisicaoNaoInternaSemChave() throws ServletException, IOException {
        assertEquals(200, executar(criarFiltro(true, "chave-correta"), "/clientes", null));
    }

    @Test
    void deveManterSegurancaLigadaPorPadrao() {
        assertTrue(new SegurancaProperties(null).enabled());
    }
}
