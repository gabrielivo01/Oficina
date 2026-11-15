package io.github.gabrielivo.oficina.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void deveCriarOpenApiComInformacoesDaOficina() {
        OpenApiConfig config = new OpenApiConfig();

        var openAPI = config.oficinaOpenApi();

        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertEquals("Oficina REST API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
    }
}
