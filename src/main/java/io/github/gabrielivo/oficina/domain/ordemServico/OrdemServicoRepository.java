package io.github.gabrielivo.oficina.domain.ordemServico;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, String> {
    List<OrdemServico> findByVeiculoId(String veiculoId);

    List<OrdemServico> findByClienteId(String clienteId);
}
