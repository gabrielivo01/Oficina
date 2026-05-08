package io.github.gabrielivo.oficina.domain.veiculo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VeiculoRepository extends JpaRepository<Veiculo, String> {
    boolean existsByPlaca(String placa);
    List<Veiculo> findByClienteId(String clienteId);
}
