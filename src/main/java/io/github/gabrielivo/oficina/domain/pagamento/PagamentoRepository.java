package io.github.gabrielivo.oficina.domain.pagamento;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PagamentoRepository extends JpaRepository<Pagamento, String> {
    List<Pagamento> findByOrdemServicoId(String ordemServicoId);
    boolean existsByOrdemServicoId(String ordemServicoId);
}