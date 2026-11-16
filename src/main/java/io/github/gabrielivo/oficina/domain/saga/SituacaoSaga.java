package io.github.gabrielivo.oficina.domain.saga;

public enum SituacaoSaga {
    EM_ANDAMENTO,
    COMPENSANDO,
    CONCLUIDA,
    COMPENSADA,
    FALHA_COMPENSACAO
}
