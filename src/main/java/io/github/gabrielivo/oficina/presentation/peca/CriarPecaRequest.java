package io.github.gabrielivo.oficina.presentation.peca;

import java.math.BigDecimal;

public record CriarPecaRequest(
    String nome,
    String descricao,
    BigDecimal preco,
    Integer quantidadeEstoque,
    Integer estoqueMinimo
) {}