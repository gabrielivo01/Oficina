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
        if (usuarioRepository.findByLogin(command.login()).isPresent()) {
            throw new UsuarioException("Login já cadastrado: " + command.login());
        }

        String senhaCriptografada = passwordEncoder.encode(command.senha());
        Usuario usuario = new Usuario(command.login(), senhaCriptografada);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void trocarSenha(TrocarSenhaCommand command) {
        Usuario usuario = usuarioRepository.findByLogin(command.login())
                .orElseThrow(() -> new UsuarioException("Usuário não encontrado."));

        if (!passwordEncoder.matches(command.senhaAtual(), usuario.getSenha())) {
            throw new UsuarioException("Senha atual incorreta.");
        }

        Usuario atualizado = new Usuario(usuario.getLogin(), passwordEncoder.encode(command.novaSenha()));
        usuarioRepository.save(atualizado);
    }
}