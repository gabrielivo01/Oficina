package io.github.gabrielivo.oficina.domain.ordemServico;

public enum StatusOrdemServico {
    RECEBIDA,
    EM_DIAGNOSTICO,
    AGUARDANDO_APROVACAO,
    EM_EXECUCAO,
    FINALIZADA,
    ENTREGUE;

    public boolean podeTransicionarPara(StatusOrdemServico proximo) {
        return switch (this) {
            case RECEBIDA -> proximo == EM_DIAGNOSTICO;
            case EM_DIAGNOSTICO -> proximo == AGUARDANDO_APROVACAO;
            case AGUARDANDO_APROVACAO -> proximo == EM_EXECUCAO;
            case EM_EXECUCAO -> proximo == FINALIZADA;
            case FINALIZADA -> proximo == ENTREGUE;
            case ENTREGUE -> false;
        };
    }

    public StatusOrdemServico proximoStatus() {
        return switch (this) {
            case RECEBIDA -> EM_DIAGNOSTICO;
            case EM_DIAGNOSTICO -> AGUARDANDO_APROVACAO;
            case AGUARDANDO_APROVACAO -> EM_EXECUCAO;
            case EM_EXECUCAO -> FINALIZADA;
            case FINALIZADA -> ENTREGUE;
            case ENTREGUE -> null;
        };
    }
}
