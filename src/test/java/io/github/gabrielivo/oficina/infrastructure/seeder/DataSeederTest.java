package io.github.gabrielivo.oficina.infrastructure.seeder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataSeeder dataSeeder;

    @Test
    void testRun_CriaUsuarioAdminQuandoNaoExiste() {
        // Arrange
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin123")).thenReturn("encodedPassword");
        // Act
        dataSeeder.run(null);

        // Assert
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void testRun_NaoCriaUsuarioAdminQuandoJaExiste() {
        // Arrange
        Usuario admin = new Usuario("admin", "existingPassword");
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(admin));
        // Act
        dataSeeder.run(null);

        // Assert
        verify(usuarioRepository, never()).save(any());
    }
}
