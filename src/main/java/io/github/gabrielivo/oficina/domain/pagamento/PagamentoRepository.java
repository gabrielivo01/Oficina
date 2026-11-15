package io.github.gabrielivo.oficina.domain.pagamento;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoRepository extends JpaRepository<Pagamento, String> {
    List<Pagamento> findByOrdemServicoId(String ordemServicoId);

    boolean existsByOrdemServicoId(String ordemServicoId);
}
