package io.github.gabrielivo.oficina.domain.pagamento;

public class PagamentoException extends RuntimeException {
    public PagamentoException(String message) {
        super(message);
    }
}