package io.github.gabrielivo.oficina.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final ClienteUserDetailsService clienteUserDetailsService;

    public JwtAuthFilter(
        JwtService jwtService,
        UserDetailsServiceImpl userDetailsService,
        ClienteUserDetailsService clienteUserDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.clienteUserDetailsService = clienteUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValido(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = jwtService.extrairLogin(token);

        if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                TipoPrincipal tipo = jwtService.extrairTipo(token);
                UserDetails userDetails = tipo == TipoPrincipal.CLIENTE
                    ? clienteUserDetailsService.loadUserByUsername(subject)
                    : userDetailsService.loadUserByUsername(subject);

                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (UsernameNotFoundException e) {
                // Token com assinatura válida mas cujo titular não existe mais (ou foi
                // inativado desde a emissão) — segue sem autenticar.
            }
        }

        filterChain.doFilter(request, response);
    }
}
