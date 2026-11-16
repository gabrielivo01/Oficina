package io.github.gabrielivo.oficina.application.mensageria;

/** A mensagem não foi confirmada pelo broker; continua pendente no outbox e será tentada de novo. */
public class FalhaPublicacaoException extends RuntimeException {

    public FalhaPublicacaoException(String mensagem) {
        super(mensagem);
    }

    public FalhaPublicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
