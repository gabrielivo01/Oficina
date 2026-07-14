package io.github.gabrielivo.oficina.application.auth;

import io.github.gabrielivo.oficina.infrastructure.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public String autenticar(String login, String senha) {
        var authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(login, senha)
        );

        return jwtService.gerarToken(authentication.getName());
    }
}
