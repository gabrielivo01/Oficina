package io.github.gabrielivo.oficina.presentation;

import java.util.List;

/** Página de resultados de uma listagem paginada (contrato estável, independente do {@code Page} do Spring Data). */
public record PaginaResponse<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {}
