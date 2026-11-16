package io.github.gabrielivo.oficina.domain.mensageria;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemOutboxRepository extends JpaRepository<MensagemOutbox, String> {

    /** Pendentes na ordem de gravação (preserva a ordem das mensagens de uma mesma OS). */
    List<MensagemOutbox> findByEnviadoEmIsNullOrderByCriadoEmAscIdAsc(Limit limite);

    long countByEnviadoEmIsNull();
}
