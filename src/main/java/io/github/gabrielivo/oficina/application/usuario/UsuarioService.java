package io.github.gabrielivo.oficina.application.usuario;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gabrielivo.oficina.domain.usuario.Usuario;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioException;
import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario criar(CriarUsuarioCommand command) {
        validarLoginDisponivel(command.login());

        Usuario usuario = new Usuario(command.login(), passwordEncoder.encode(command.senha()));
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void trocarSenha(TrocarSenhaCommand command) {
        Usuario usuario = buscarUsuarioPorLogin(command.login());
        validarSenhaAtual(command.senhaAtual(), usuario);

        usuario.atualizarSenha(passwordEncoder.encode(command.novaSenha()));
        usuarioRepository.save(usuario);
    }

    private void validarLoginDisponivel(String login) {
        if (usuarioRepository.findByLogin(login).isPresent()) {
            throw new UsuarioException("Login já cadastrado: " + login);
        }
    }

    private Usuario buscarUsuarioPorLogin(String login) {
        return usuarioRepository.findByLogin(login)
            .orElseThrow(() -> new UsuarioException("Usuário não encontrado."));
    }

    private void validarSenhaAtual(String senhaAtual, Usuario usuario) {
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
            throw new UsuarioException("Senha atual incorreta.");
        }
    }
}