package io.github.gabrielivo.oficina.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Regressão: publicar mais de um bean UserDetailsService (UserDetailsServiceImpl
 * para staff, ClienteUserDetailsService para CPF) faz o autoconfig do Spring
 * Security desistir de montar um DaoAuthenticationProvider sozinho; sem o bean
 * explícito AuthenticationProvider em SecurityConfig, o AuthenticationManager
 * exposto acaba com si mesmo como parent e authenticate() entra em recursão
 * infinita (StackOverflowError) em vez de autenticar. Só um teste que resolve
 * o AuthenticationManager REAL do contexto (não um mock) pega isso.
 */
@SpringBootTest(
        properties = {
            // Contexto H2 sem broker: não consome nem publica (e não toca um RabbitMQ de dev que esteja no ar)
            "spring.rabbitmq.listener.simple.auto-startup=false",
            "app.mensageria.outbox.publicador-habilitado=false"
        })
class AuthenticationManagerWiringTest {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    void deveAutenticarUsuarioStaffComCredenciaisValidas() {
        Authentication result =
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("admin", "admin123"));

        assertTrue(result.isAuthenticated());
        assertEquals("admin", result.getName());
    }

    @Test
    void deveRecusarCredenciaisInvalidasSemEstourarPilha() {
        assertThrows(
                BadCredentialsException.class,
                () -> authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken("admin", "senha-errada")));
    }
}
