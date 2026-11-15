package io.github.gabrielivo.oficina.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emite (staff) e valida (staff e cliente) tokens HS256. O formato é contrato com a oficina-auth-lambda: {@code sub},
 * {@code iat}, {@code exp} e, só no token de cliente, a claim {@code tipo=CLIENTE}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiration;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    public String gerarToken(String login) {
        return Jwts.builder()
                .subject(login)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                // Algoritmo fixo: sem ele o jjwt 0.12 escolheria HS384/HS512 para segredos maiores que 256 bits.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String extrairLogin(String token) {
        return getClaims(token).getSubject();
    }

    public TipoPrincipal extrairTipo(String token) {
        String tipo = getClaims(token).get("tipo", String.class);
        return tipo == null ? TipoPrincipal.USUARIO : TipoPrincipal.valueOf(tipo);
    }

    public boolean isTokenValido(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
