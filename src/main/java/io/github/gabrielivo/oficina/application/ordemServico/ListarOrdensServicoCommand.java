package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.util.EnumSet;
import java.util.Set;

/**
 * Filtro e página da listagem de OS. Sem status informado, lista as OS ativas (não finalizadas, entregues nem
 * canceladas).
 */
public record ListarOrdensServicoCommand(Set<StatusOrdemServico> status, int pagina, int tamanho) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    public ListarOrdensServicoCommand {
        if (pagina < 0) {
            throw new IllegalArgumentException("Página deve ser maior ou igual a zero.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        status = status == null || status.isEmpty() ? StatusOrdemServico.ativos() : Set.copyOf(EnumSet.copyOf(status));
    }
}
