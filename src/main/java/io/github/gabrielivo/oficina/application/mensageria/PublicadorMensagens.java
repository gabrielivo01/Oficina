package io.github.gabrielivo.oficina.application.mensageria;

/** Porta de publicação no broker. O adapter só retorna depois da confirmação (ack) do broker. */
public interface PublicadorMensagens {

    /**
     * Publica a mensagem de forma persistente e espera o publisher confirm.
     *
     * @throws FalhaPublicacaoException se o broker recusar (nack), não rotear a mensagem para nenhuma fila, ou não
     *     confirmar dentro do prazo
     */
    void publicar(MensagemPublicavel mensagem);
}
