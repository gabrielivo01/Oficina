package io.github.gabrielivo.oficina.presentation.auth;

import io.github.gabrielivo.oficina.application.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void deveRetornarTokenQuandoLoginValido() {
        LoginRequest request = new LoginRequest("usuario.teste", "senha123");

        when(authService.autenticar("usuario.teste", "senha123")).thenReturn("token-jwt");

        ResponseEntity<LoginResponse> response = authController.login(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("token-jwt", response.getBody().token());

        verify(authService).autenticar("usuario.teste", "senha123");
    }
}
