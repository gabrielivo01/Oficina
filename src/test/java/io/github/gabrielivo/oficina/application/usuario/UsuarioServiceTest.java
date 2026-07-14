package io.github.gabrielivo.oficina.application.usuario;

import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioException;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void deveCriarUsuarioQuandoLoginDisponivel() {
        var command = new CriarUsuarioCommand("usuario.teste", "senha123");

        when(usuarioRepository.findByLogin("usuario.teste")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("senha-criptografada");

        usuarioService.criar(command);

        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void deveImpedirCriacaoQuandoLoginJaExiste() {
        var command = new CriarUsuarioCommand("usuario.teste", "senha123");
        when(usuarioRepository.findByLogin("usuario.teste")).thenReturn(Optional.of(new Usuario("usuario.teste", "senha")));

        UsuarioException exception = assertThrows(UsuarioException.class, () -> usuarioService.criar(command));

        assertEquals("Login já cadastrado: usuario.teste", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveTrocarSenhaDoUsuarioExistente() {
        var usuario = new Usuario("usuario.teste", "senha-atual-criptografada");
        var command = new TrocarSenhaCommand("usuario.teste", "senha-atual", "nova-senha");

        when(usuarioRepository.findByLogin("usuario.teste")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-atual", "senha-atual-criptografada")).thenReturn(true);
        when(passwordEncoder.encode("nova-senha")).thenReturn("nova-senha-criptografada");

        usuarioService.trocarSenha(command);

        assertEquals("nova-senha-criptografada", usuario.getSenha());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveFalharAoTrocarSenhaQuandoSenhaAtualForIncorreta() {
        var usuario = new Usuario("usuario.teste", "senha-atual-criptografada");
        var command = new TrocarSenhaCommand("usuario.teste", "senha-errada", "nova-senha");

        when(usuarioRepository.findByLogin("usuario.teste")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-errada", "senha-atual-criptografada")).thenReturn(false);

        UsuarioException exception = assertThrows(UsuarioException.class, () -> usuarioService.trocarSenha(command));

        assertEquals("Senha atual incorreta.", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }
}