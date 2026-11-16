package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import io.github.gabrielivo.oficina.application.mensageria.OutboxService;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServicoAlterado;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicadorEventosOrdemServico {

    private final OrdemServicoRepository ordemServicoRepository;
    private final OutboxService outboxService;
    private final MensageriaProperties mensageria;

    public PublicadorEventosOrdemServico(
            OrdemServicoRepository ordemServicoRepository,
            OutboxService outboxService,
            MensageriaProperties mensageria) {
        this.ordemServicoRepository = ordemServicoRepository;
        this.outboxService = outboxService;
        this.mensageria = mensageria;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OrdemServico salvarEPublicar(OrdemServico os) {
        OrdemServico salva = ordemServicoRepository.save(os);
        for (StatusOrdemServicoAlterado evento : os.extrairEventos()) {
            var alteradoEm = evento.alteradoEm().atZone(ZoneId.systemDefault()).toInstant();
            outboxService.registrar(
                    mensageria.exchangeEventos(),
                    StatusOrdemServicoAlteradoPayload.ROUTING_KEY,
                    StatusOrdemServicoAlteradoPayload.TIPO,
                    evento.ordemServicoId(),
                    alteradoEm,
                    new StatusOrdemServicoAlteradoPayload(
                            evento.statusAnterior(),
                            evento.statusNovo(),
                            evento.origem(),
                            evento.motivo(),
                            alteradoEm));
        }
        return salva;
    }
}
