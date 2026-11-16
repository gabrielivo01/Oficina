package io.github.gabrielivo.oficina.application.mensageria;

import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutbox;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Grava mensagens no outbox (na transação do chamador) e mantém o estado de envio. */
@Service
public class OutboxService {

    private final MensagemOutboxRepository repository;
    private final JsonMapper jsonMapper;
    private final ContextoRastreamento contextoRastreamento;

    public OutboxService(
            MensagemOutboxRepository repository, JsonMapper jsonMapper, ContextoRastreamento contextoRastreamento) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.contextoRastreamento = contextoRastreamento;
    }

    /**
     * Monta o envelope e o grava no outbox. Exige transação ativa: a mensagem só existe se a mudança de estado que a
     * originou for confirmada (nunca publicar direto de dentro de {@code @Transactional}). Guarda também o
     * {@code traceparent} atual, para a publicação e o consumo ficarem no mesmo trace da requisição.
     *
     * @return o envelope gravado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public EnvelopeMensagem registrar(
            String exchange, String routingKey, String tipo, String osId, Instant occurredAt, Object payload) {
        EnvelopeMensagem envelope = new EnvelopeMensagem(
                UUID.randomUUID().toString(), tipo, osId, osId, occurredAt, jsonMapper.valueToTree(payload));
        repository.save(new MensagemOutbox(
                envelope.messageId(),
                tipo,
                exchange,
                routingKey,
                jsonMapper.writeValueAsString(envelope),
                contextoRastreamento.traceparentAtual()));
        return envelope;
    }

    @Transactional(readOnly = true)
    public List<MensagemOutbox> buscarPendentes(int limite) {
        return repository.findByEnviadoEmIsNullOrderByCriadoEmAscIdAsc(Limit.of(limite));
    }

    @Transactional
    public void marcarEnviada(String id) {
        repository.findById(id).ifPresent(MensagemOutbox::marcarEnviada);
    }

    @Transactional
    public void registrarFalha(String id, String erro) {
        repository.findById(id).ifPresent(m -> m.registrarFalha(erro));
    }

    @Transactional(readOnly = true)
    public long contarPendentes() {
        return repository.countByEnviadoEmIsNull();
    }
}
