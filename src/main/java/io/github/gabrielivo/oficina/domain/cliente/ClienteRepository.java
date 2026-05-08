package io.github.gabrielivo.oficina.domain.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, String> {
    boolean existsByCpf(String cpf);
    Optional<Cliente> findByCpf(String cpf);
}