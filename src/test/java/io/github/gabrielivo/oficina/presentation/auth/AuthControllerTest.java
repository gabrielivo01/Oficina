package io.github.gabrielivo.oficina.presentation.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import io.github.gabrielivo.oficina.infrastructure.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthController authController;

    @Test
    void deveRetornarTokenQuandoLoginValido() {
        LoginRequest request = new LoginRequest("usuario.teste", "senha123");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getName()).thenReturn("usuario.teste");
        when(jwtService.gerarToken("usuario.teste")).thenReturn("token-jwt");

        ResponseEntity<LoginResponse> response = authController.login(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("token-jwt", response.getBody().token());

        verify(authenticationManager).authenticate(any());
        verify(jwtService).gerarToken("usuario.teste");
    }
}
