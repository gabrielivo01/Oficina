package io.github.gabrielivo.oficina.application.mensageria;

import io.github.gabrielivo.oficina.domain.mensageria.MensagemProcessada;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemProcessadaRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Componente de idempotência (inbox) usado pelos listeners: numa única transação, registra o {@code messageId} em
 * {@code inbox} e executa o manipulador do tipo. Uma duplicata (mesmo {@code messageId}) é ignorada. Se duas cópias
 * chegarem ao mesmo tempo, a chave primária do inbox faz uma delas falhar e voltar; na nova tentativa ela é ignorada.
 */
@Service
public class ProcessadorMensagensService {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorMensagensService.class);

    private final MensagemProcessadaRepository inbox;
    private final Map<String, ManipuladorMensagem> manipuladores = new HashMap<>();

    public ProcessadorMensagensService(MensagemProcessadaRepository inbox, List<ManipuladorMensagem> manipuladores) {
        this.inbox = inbox;
        for (ManipuladorMensagem manipulador : manipuladores) {
            ManipuladorMensagem anterior = this.manipuladores.put(manipulador.tipo(), manipulador);
            if (anterior != null) {
                throw new IllegalStateException("Dois manipuladores para o tipo " + manipulador.tipo());
            }
        }
    }

    /** @return {@code true} se processou; {@code false} se era duplicata */
    @Transactional
    public boolean processar(EnvelopeMensagem envelope) {
        ManipuladorMensagem manipulador = manipuladores.get(envelope.type());
        if (manipulador == null) {
            throw new MensagemNaoSuportadaException(envelope.type());
        }
        if (inbox.existsById(envelope.messageId())) {
            log.info("Mensagem {} ({}) já processada; ignorada.", envelope.messageId(), envelope.type());
            return false;
        }
        inbox.saveAndFlush(new MensagemProcessada(envelope.messageId(), envelope.type()));
        manipulador.manipular(envelope);
        return true;
    }
}
