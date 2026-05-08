package io.github.gabrielivo.oficina.infrastructure.security;

import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void deveCarregarUsuarioPorLoginQuandoExiste() {
        Usuario usuario = new Usuario("usuario.teste", "senha123");
        when(usuarioRepository.findByLogin("usuario.teste")).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsService.loadUserByUsername("usuario.teste");

        assertNotNull(userDetails);
        assertEquals("usuario.teste", userDetails.getUsername());
        assertEquals("senha123", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void deveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByLogin("usuario.desconhecido")).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
            UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("usuario.desconhecido")
        );

        assertEquals("Usuário não encontrado: usuario.desconhecido", exception.getMessage());
    }
}
