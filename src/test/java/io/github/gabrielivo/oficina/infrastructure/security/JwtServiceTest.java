package io.github.gabrielivo.oficina.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SEGREDO = "01234567890123456789012345678901";
    /** Segredo com mais de 256 bits: sem fixar o algoritmo, o jjwt 0.12 escolheria HS384. */
    private static final String SEGREDO_LONGO = "sua-chave-secreta-deve-ter-pelo-menos-256-bits-aqui";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SEGREDO, 10000L));
    }

    private static SecretKey chave(String segredo) {
        return Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
    }

    private static String cabecalho(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
    }

    private static String corpo(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    @Test
    void deveGerarTokenValido() {
        String token = jwtService.gerarToken("usuario.teste");

        assertNotNull(token);
        assertTrue(jwtService.isTokenValido(token));
        assertEquals("usuario.teste", jwtService.extrairLogin(token));
    }

    @Test
    void deveManterFormatoDoTokenDeStaffHs256ComSubIatExpESemTipo() {
        JwtService comSegredoLongo = new JwtService(new JwtProperties(SEGREDO_LONGO, 10000L));

        String token = comSegredoLongo.gerarToken("admin");

        assertTrue(cabecalho(token).contains("\"alg\":\"HS256\""), cabecalho(token));
        String claims = corpo(token);
        assertTrue(claims.contains("\"sub\":\"admin\""), claims);
        assertTrue(claims.contains("\"iat\":"), claims);
        assertTrue(claims.contains("\"exp\":"), claims);
        assertFalse(claims.contains("\"tipo\""), claims);
    }

    @Test
    void deveRetornarFalseParaTokenInvalido() {
        assertFalse(jwtService.isTokenValido("token-invalido"));
        assertFalse(jwtService.isTokenValido(null));
    }

    @Test
    void deveRecusarTokenAssinadoComOutroSegredo() {
        String token = Jwts.builder()
                .subject("intruso")
                .signWith(chave("outro-segredo-com-pelo-menos-32-bytes!!"), Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.isTokenValido(token));
    }

    @Test
    void deveRecusarTokenExpirado() {
        String token = Jwts.builder()
                .subject("usuario.teste")
                .issuedAt(new Date(System.currentTimeMillis() - 20000L))
                .expiration(new Date(System.currentTimeMillis() - 10000L))
                .signWith(chave(SEGREDO), Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.isTokenValido(token));
    }

    @Test
    void deveRetornarTipoUsuarioPorPadraoQuandoClaimAusente() {
        String token = jwtService.gerarToken("usuario.teste");

        assertEquals(TipoPrincipal.USUARIO, jwtService.extrairTipo(token));
    }

    @Test
    void deveAceitarTokenNoFormatoEmitidoPelaLambdaDeCpf() {
        // Mesmo formato do JwtIssuer da oficina-auth-lambda: HS256, sub=CPF, tipo=CLIENTE, iat e exp.
        String token = Jwts.builder()
                .subject("11122233344")
                .claim("tipo", "CLIENTE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 10000L))
                .signWith(chave(SEGREDO), Jwts.SIG.HS256)
                .compact();

        assertTrue(jwtService.isTokenValido(token));
        assertEquals(TipoPrincipal.CLIENTE, jwtService.extrairTipo(token));
        assertEquals("11122233344", jwtService.extrairLogin(token));
    }
}
