package io.github.gabrielivo.oficina.infrastructure.security;

import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ClienteUserDetailsService implements UserDetailsService {

    private final ClienteRepository clienteRepository;

    public ClienteUserDetailsService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String cpf) throws UsernameNotFoundException {
        return clienteRepository.findByCpf(cpf)
            .filter(cliente -> cliente.isAtivo())
            .map(cliente -> User.builder()
                .username(cliente.getCpf())
                .password("")
                .roles("CLIENTE")
                .build()
            )
            .orElseThrow(() -> new UsernameNotFoundException("Cliente não encontrado ou inativo: " + cpf));
    }
}
