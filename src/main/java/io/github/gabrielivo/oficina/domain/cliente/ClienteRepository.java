package io.github.gabrielivo.oficina.domain.cliente;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, String> {
    boolean existsByCpf(String cpf);

    Optional<Cliente> findByCpf(String cpf);
}
