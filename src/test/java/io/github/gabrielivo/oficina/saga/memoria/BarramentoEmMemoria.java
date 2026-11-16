package io.github.gabrielivo.oficina.saga.memoria;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.FalhaPublicacaoException;
import io.github.gabrielivo.oficina.application.mensageria.MensagemPublicavel;
import io.github.gabrielivo.oficina.application.mensageria.PublicadorMensagens;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implementação de teste da porta {@link PublicadorMensagens} que imita a topologia de
 * {@code oficina-infra-k8s/local/rabbitmq/definitions.json}: comandos em {@code oficina.comandos} vão ao participante
 * da fila indicada pela routing key; eventos em {@code oficina.eventos} vão para a fila {@code os.saga.eventos},
 * consumida pelo OS Service. Mensagem sem destino falha como o publisher confirm com {@code mandatory=true}.
 *
 * <p>A entrega é síncrona, mas os eventos ficam enfileirados até {@link #proximoEvento()}: quem processa um evento
 * nunca é chamado de dentro da publicação de outro.
 */
public final class BarramentoEmMemoria implements PublicadorMensagens {

    static final String EXCHANGE_COMANDOS = "oficina.comandos";
    static final String EXCHANGE_EVENTOS = "oficina.eventos";

    private final JsonMapper jsonMapper;
    private final Map<String, ParticipanteFalso> participantes = new HashMap<>();
    private final Deque<EnvelopeMensagem> filaEventosOs = new ArrayDeque<>();
    private final List<EnvelopeMensagem> eventosEntregues = new ArrayList<>();

    public BarramentoEmMemoria(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void registrar(ParticipanteFalso participante) {
        participantes.put(participante.fila(), participante);
    }

    @Override
    public void publicar(MensagemPublicavel mensagem) {
        EnvelopeMensagem envelope = jsonMapper.readValue(mensagem.corpo(), EnvelopeMensagem.class);
        switch (mensagem.exchange()) {
            case EXCHANGE_COMANDOS -> {
                ParticipanteFalso destino = participantes.get(mensagem.routingKey());
                if (destino == null) {
                    throw new FalhaPublicacaoException("Comando sem fila de destino: " + mensagem.routingKey());
                }
                destino.receber(envelope);
            }
            case EXCHANGE_EVENTOS -> publicarEvento(envelope);
            default -> throw new FalhaPublicacaoException("Exchange desconhecida: " + mensagem.exchange());
        }
    }

    /** Evento publicado por um participante (ou pelo próprio OS Service) com destino à fila {@code os.saga.eventos}. */
    public void publicarEvento(EnvelopeMensagem evento) {
        filaEventosOs.addLast(evento);
    }

    /** @return o próximo evento da fila do OS Service, ou {@code null} se ela estiver vazia */
    EnvelopeMensagem proximoEvento() {
        EnvelopeMensagem evento = filaEventosOs.pollFirst();
        if (evento != null) {
            eventosEntregues.add(evento);
        }
        return evento;
    }

    /** Tipos dos eventos entregues ao OS Service para a OS, na ordem de entrega. */
    public List<String> eventosEntregues(String osId) {
        return eventosEntregues.stream()
                .filter(evento -> osId.equals(evento.osId()))
                .map(EnvelopeMensagem::type)
                .toList();
    }
}
