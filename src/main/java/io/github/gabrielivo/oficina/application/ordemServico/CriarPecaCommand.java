package io.github.gabrielivo.oficina.application.ordemServico;

import java.math.BigDecimal;

public record CriarPecaCommand(
    String nome,
    String descricao,
    BigDecimal preco,
    Integer quantidadeEstoque,
    Integer estoqueMinimo
) {}