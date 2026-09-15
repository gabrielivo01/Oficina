package io.github.gabrielivo.oficina.infrastructure.security;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
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
class ClienteUserDetailsServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteUserDetailsService clienteUserDetailsService;

    @Test
    void deveCarregarClientePorCpfQuandoExisteEAtivo() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(cliente));

        UserDetails userDetails = clienteUserDetailsService.loadUserByUsername("11122233344");

        assertNotNull(userDetails);
        assertEquals("11122233344", userDetails.getUsername());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE")));
    }

    @Test
    void deveLancarExcecaoQuandoCpfNaoCadastrado() {
        when(clienteRepository.findByCpf("00000000000")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> clienteUserDetailsService.loadUserByUsername("00000000000"));
    }

    @Test
    void deveLancarExcecaoQuandoClienteInativo() {
        Cliente cliente = new Cliente("55566677788", "Cliente Inativo", "61999999999", null);
        cliente.inativar();
        when(clienteRepository.findByCpf("55566677788")).thenReturn(Optional.of(cliente));

        assertThrows(UsernameNotFoundException.class,
            () -> clienteUserDetailsService.loadUserByUsername("55566677788"));
    }
}
