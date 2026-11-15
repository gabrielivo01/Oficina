package io.github.gabrielivo.oficina.domain.veiculo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VeiculoRepository extends JpaRepository<Veiculo, String> {
    boolean existsByPlaca(String placa);

    List<Veiculo> findByClienteId(String clienteId);
}
