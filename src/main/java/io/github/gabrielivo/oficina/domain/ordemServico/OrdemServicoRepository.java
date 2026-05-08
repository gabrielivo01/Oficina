package io.github.gabrielivo.oficina.domain.ordemServico;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, String> {
    List<OrdemServico> findByVeiculoId(String veiculoId);
    List<OrdemServico> findByClienteId(String clienteId);
}