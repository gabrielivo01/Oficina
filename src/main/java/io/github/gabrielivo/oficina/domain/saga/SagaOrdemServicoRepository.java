package io.github.gabrielivo.oficina.domain.saga;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaOrdemServicoRepository extends JpaRepository<SagaOrdemServico, String> {

    List<SagaOrdemServico> findTop100ByDiagnosticoSolicitadoFalseAndSituacaoAndEtapaAtualOrderByCriadoEmAsc(
            SituacaoSaga situacao, EtapaSaga etapa);

    List<SagaOrdemServico> findTop100BySituacaoAndPrazoExpiracaoLessThanEqualOrderByPrazoExpiracaoAsc(
            SituacaoSaga situacao, Instant agora);
}
