package io.github.gabrielivo.oficina.domain.ordemServico;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status oficial da OS (seção 3.1 do plano da Fase 4):
 *
 * <pre>
 * RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → AGUARDANDO_PAGAMENTO → EM_EXECUCAO → FINALIZADA → ENTREGUE
 * (qualquer status antes de FINALIZADA) → CANCELADA
 * </pre>
 */
public enum StatusOrdemServico {
    RECEBIDA,
    EM_DIAGNOSTICO,
    AGUARDANDO_APROVACAO,
    AGUARDANDO_PAGAMENTO,
    EM_EXECUCAO,
    FINALIZADA,
    ENTREGUE,
    CANCELADA;

    public boolean podeTransicionarPara(StatusOrdemServico proximo) {
        if (proximo == null) {
            return false;
        }
        if (proximo == CANCELADA) {
            return isAtivo();
        }
        return proximo == proximoStatus();
    }

    /** Próximo status do caminho feliz; {@code null} para os status finais. */
    public StatusOrdemServico proximoStatus() {
        return switch (this) {
            case RECEBIDA -> EM_DIAGNOSTICO;
            case EM_DIAGNOSTICO -> AGUARDANDO_APROVACAO;
            case AGUARDANDO_APROVACAO -> AGUARDANDO_PAGAMENTO;
            case AGUARDANDO_PAGAMENTO -> EM_EXECUCAO;
            case EM_EXECUCAO -> FINALIZADA;
            case FINALIZADA -> ENTREGUE;
            case ENTREGUE, CANCELADA -> null;
        };
    }

    /** OS em andamento: ainda não finalizada, entregue nem cancelada. */
    public boolean isAtivo() {
        return this != FINALIZADA && this != ENTREGUE && this != CANCELADA;
    }

    public static Set<StatusOrdemServico> ativos() {
        EnumSet<StatusOrdemServico> ativos = EnumSet.noneOf(StatusOrdemServico.class);
        for (StatusOrdemServico status : values()) {
            if (status.isAtivo()) {
                ativos.add(status);
            }
        }
        return ativos;
    }
}
