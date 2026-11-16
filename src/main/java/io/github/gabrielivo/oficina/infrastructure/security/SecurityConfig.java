package io.github.gabrielivo.oficina.infrastructure.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({SegurancaProperties.class, InternalApiProperties.class, JwtProperties.class})
public class SecurityConfig {

    private final boolean securityEnabled;
    private final JwtAuthFilter jwtAuthFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;

    public SecurityConfig(
            SegurancaProperties seguranca, JwtAuthFilter jwtAuthFilter, InternalApiKeyFilter internalApiKeyFilter) {
        this.securityEnabled = seguranca.enabled();
        this.jwtAuthFilter = jwtAuthFilter;
        this.internalApiKeyFilter = internalApiKeyFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            // Segurança desabilitada: permitir tudo
            // CSRF não se aplica: autenticação só por JWT no header Authorization, sem cookie de sessão.
            http.csrf(csrf -> csrf.disable()) // NOSONAR: API stateless (JWT)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        // Segurança habilitada: configuração normal
        // CSRF não se aplica: autenticação só por JWT no header Authorization, sem cookie de sessão.
        http.csrf(csrf -> csrf.disable()) // NOSONAR: API stateless (JWT)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/",
                                "/actuator/**",
                                "/auth/**",
                                "/internal/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**",
                                "/favicon.ico")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Login por login/senha (AuthService) autentica só contra UserDetailsServiceImpl
    // (usuarios de staff). ClienteUserDetailsService (CPF) nunca passa por aqui —
    // JwtAuthFilter o chama diretamente. Sem este bean explícito, o autoconfig do
    // Spring Security encontra 2 beans de UserDetailsService, desiste de montar um
    // DaoAuthenticationProvider sozinho, e o AuthenticationManager exposto abaixo
    // vira seu próprio parent: authenticationManager.authenticate(...) entra em
    // recursão infinita (StackOverflowError) em vez de autenticar.
    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsServiceImpl userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
