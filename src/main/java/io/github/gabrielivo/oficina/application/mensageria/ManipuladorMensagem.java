package io.github.gabrielivo.oficina.application.mensageria;

import java.util.List;

/**
 * Trata um tipo de mensagem recebida. Roda dentro da transação do {@link ProcessadorMensagensService}, junto com o
 * registro no inbox: exceção desfaz tudo e a mensagem volta para nova tentativa.
 */
public interface ManipuladorMensagem {

    /** Valor de {@code type} do envelope que este manipulador trata. */
    String tipo();

    default List<String> tipos() {
        return List.of(tipo());
    }

    void manipular(EnvelopeMensagem envelope);
}
