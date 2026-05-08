package io.github.gabrielivo.oficina.infrastructure.seeder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.gabrielivo.oficina.application.ordemServico.CriarPecaCommand;
import io.github.gabrielivo.oficina.application.ordemServico.PecaService;
import io.github.gabrielivo.oficina.domain.peca.PecaRepository;
import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PecaRepository pecaRepository;

    @Mock
    private PecaService pecaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataSeeder dataSeeder;

    @Test
    void testRun_CriaUsuarioAdminQuandoNaoExiste() {
        // Arrange
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin123")).thenReturn("encodedPassword");
        when(pecaRepository.count()).thenReturn(1L); // Pecas ja existem, nao cria

        // Act
        dataSeeder.run(null);

        // Assert
        verify(usuarioRepository).save(any(Usuario.class));
        verify(pecaService, never()).criar(any());
    }

    @Test
    void testRun_NaoCriaUsuarioAdminQuandoJaExiste() {
        // Arrange
        Usuario admin = new Usuario("admin", "existingPassword");
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(admin));
        when(pecaRepository.count()).thenReturn(1L);

        // Act
        dataSeeder.run(null);

        // Assert
        verify(usuarioRepository, never()).save(any());
        verify(pecaService, never()).criar(any());
    }

    @Test
    void testRun_CriaPecasQuandoNaoExistem() {
        // Arrange
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(new Usuario("admin", "pass")));
        when(pecaRepository.count()).thenReturn(0L);

        // Act
        dataSeeder.run(null);

        // Assert
        verify(pecaService, times(5)).criar(any(CriarPecaCommand.class)); // 5 pecas criadas
        verify(pecaService).criar(eq(new CriarPecaCommand("Filtro de Óleo", "Filtro de óleo para motores", new BigDecimal("25.50"), 50, 10)));
        verify(pecaService).criar(eq(new CriarPecaCommand("Pastilha de Freio", "Pastilha de freio dianteira", new BigDecimal("120.00"), 20, 5)));
        verify(pecaService).criar(eq(new CriarPecaCommand("Bateria 60Ah", "Bateria automotiva 60Ah", new BigDecimal("350.00"), 15, 3)));
        verify(pecaService).criar(eq(new CriarPecaCommand("Óleo 5W30", "Óleo lubrificante sintético", new BigDecimal("45.00"), 30, 8)));
        verify(pecaService).criar(eq(new CriarPecaCommand("Velas de Ignição", "Jogo com 4 velas", new BigDecimal("85.00"), 25, 6)));
    }

    @Test
    void testRun_NaoCriaPecasQuandoJaExistem() {
        // Arrange
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(new Usuario("admin", "pass")));
        when(pecaRepository.count()).thenReturn(5L);

        // Act
        dataSeeder.run(null);

        // Assert
        verify(pecaService, never()).criar(any());
    }
}