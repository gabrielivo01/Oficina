package io.github.gabrielivo.oficina.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtService, "expiration", 10000L);
    }

    @Test
    void deveGerarTokenValido() {
        String token = jwtService.gerarToken("usuario.teste");

        assertNotNull(token);
        assertTrue(jwtService.isTokenValido(token));
        assertEquals("usuario.teste", jwtService.extrairLogin(token));
    }

    @Test
    void deveRetornarFalseParaTokenInvalido() {
        assertFalse(jwtService.isTokenValido("token-invalido"));
    }
}
