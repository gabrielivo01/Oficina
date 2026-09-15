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

    @Test
    void deveRetornarTipoUsuarioPorPadraoQuandoClaimAusente() {
        String token = jwtService.gerarToken("usuario.teste");

        assertEquals(TipoPrincipal.USUARIO, jwtService.extrairTipo(token));
    }

    @Test
    void deveExtrairTipoClienteQuandoClaimPresente() {
        String token = io.jsonwebtoken.Jwts.builder()
            .setSubject("11122233344")
            .claim("tipo", "CLIENTE")
            .setIssuedAt(new java.util.Date())
            .setExpiration(new java.util.Date(System.currentTimeMillis() + 10000L))
            .signWith(
                io.jsonwebtoken.security.Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes()),
                io.jsonwebtoken.SignatureAlgorithm.HS256)
            .compact();

        assertEquals(TipoPrincipal.CLIENTE, jwtService.extrairTipo(token));
        assertEquals("11122233344", jwtService.extrairLogin(token));
    }
}
