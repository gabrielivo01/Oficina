package io.github.gabrielivo.oficina.application.mensageria;

/** Não há manipulador para o tipo recebido: a mensagem vai para a DLQ, sem novas tentativas e sem entrar no inbox. */
public class MensagemNaoSuportadaException extends RuntimeException {

    public MensagemNaoSuportadaException(String tipo) {
        super("Nenhum manipulador para mensagens do tipo " + tipo + ".");
    }
}
