package io.github.gabrielivo.oficina.application.mensageria;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * Envelope padrão de toda mensagem entre os serviços (regra 5 do plano da Fase 4, ADR 0009; catálogo em
 * {@code documentacao/contratos_mensagens.md}). O contexto de trace vai nos headers AMQP, não aqui.
 *
 * @param messageId UUID único da mensagem (chave do inbox do consumidor)
 * @param type nome do comando/evento (ex.: {@code OrcamentoAprovado})
 * @param sagaId id da saga (= id da OS)
 * @param osId id da OS
 * @param occurredAt instante em que o fato ocorreu (ISO-8601, UTC)
 * @param payload conteúdo específico do tipo
 */
public record EnvelopeMensagem(
        String messageId, String type, String sagaId, String osId, Instant occurredAt, JsonNode payload) {

    public EnvelopeMensagem {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId é obrigatório no envelope.");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type é obrigatório no envelope.");
        }
    }
}
