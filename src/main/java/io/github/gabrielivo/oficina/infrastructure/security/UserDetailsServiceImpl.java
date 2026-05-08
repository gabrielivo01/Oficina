package io.github.gabrielivo.oficina.infrastructure.security;


import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import io.github.gabrielivo.oficina.domain.usuario.UsuarioRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UserDetailsServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return usuarioRepository.findByLogin(login)
            .map(u -> User.builder()
                .username(u.getLogin())
                .password(u.getSenha())
                .roles("USER")
                .build()
            )
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + login));
    }
}
