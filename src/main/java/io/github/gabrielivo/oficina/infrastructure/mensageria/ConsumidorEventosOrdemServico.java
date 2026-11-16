package io.github.gabrielivo.oficina.infrastructure.mensageria;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.MensagemNaoSuportadaException;
import io.github.gabrielivo.oficina.application.mensageria.ProcessadorMensagensService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor da fila de eventos do OS Service ({@code os.saga.eventos}). Converte o envelope e delega ao processador
 * idempotente (inbox). Falha transitória: exceção → retry com backoff no consumidor (3 tentativas) → rejeição → DLQ.
 * Mensagem inválida ou de tipo sem manipulador: rejeitada direto para a DLQ.
 */
@Component
public class ConsumidorEventosOrdemServico {

    private final ProcessadorMensagensService processador;
    private final JsonMapper jsonMapper;

    public ConsumidorEventosOrdemServico(ProcessadorMensagensService processador, JsonMapper jsonMapper) {
        this.processador = processador;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(id = "consumidor-eventos-os", queues = "${app.mensageria.fila-eventos:os.saga.eventos}")
    public void receber(Message mensagem) {
        EnvelopeMensagem envelope;
        try {
            envelope = jsonMapper.readValue(mensagem.getBody(), EnvelopeMensagem.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Envelope inválido: " + e.getMessage(), e);
        }
        try {
            processador.processar(envelope);
        } catch (MensagemNaoSuportadaException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
