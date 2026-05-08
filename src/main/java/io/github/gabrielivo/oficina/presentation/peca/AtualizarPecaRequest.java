package io.github.gabrielivo.oficina.presentation.peca;

import java.math.BigDecimal;

public record AtualizarPecaRequest(
    String nome,
    String descricao,
    BigDecimal preco,
    Integer quantidadeEstoque,
    Integer estoqueMinimo
) {}