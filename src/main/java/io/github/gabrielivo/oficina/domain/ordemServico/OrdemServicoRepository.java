package io.github.gabrielivo.oficina.domain.ordemServico;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, String> {
    List<OrdemServico> findByVeiculoId(String veiculoId);

    List<OrdemServico> findByClienteId(String clienteId);

    /**
     * Página de OS com os status pedidos, ordenada pela prioridade de atendimento (mais adiantadas primeiro) e depois
     * pela abertura. A ordenação é fixa: a do {@code Pageable} é ignorada.
     */
    @Query(value = """
                    select os from OrdemServico os
                    where os.status in :status
                    order by case os.status
                        when io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico.EM_EXECUCAO then 0
                        when io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico.AGUARDANDO_PAGAMENTO then 1
                        when io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico.AGUARDANDO_APROVACAO then 2
                        when io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico.EM_DIAGNOSTICO then 3
                        when io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico.RECEBIDA then 4
                        else 5
                    end, os.criadoEm, os.id
                    """, countQuery = "select count(os) from OrdemServico os where os.status in :status")
    Page<OrdemServico> buscarPorStatusOrdenadoPorPrioridade(
            @Param("status") Collection<StatusOrdemServico> status, Pageable pageable);
}
