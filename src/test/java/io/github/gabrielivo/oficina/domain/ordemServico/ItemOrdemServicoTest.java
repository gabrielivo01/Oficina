package io.github.gabrielivo.oficina.domain.ordemServico;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ItemOrdemServicoTest {

    @Test
    void deveCalcularValorTotalParaServico() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        ItemOrdemServico item = new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("150.00"));

        assertEquals(new BigDecimal("150.00"), item.getValorTotal());
        assertEquals(1, item.getQuantidade());
        assertEquals(TipoItemOrdemServico.SERVICO, item.getTipo());
    }

    @Test
    void deveCalcularValorTotalParaPecaComQuantidadeMaiorQueUm() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);
        var peca = new io.github.gabrielivo.oficina.domain.peca.Peca("Filtro", "Filtro de óleo", new BigDecimal("25.00"), 10);

        ItemOrdemServico item = new ItemOrdemServico(os, "Filtro de óleo", new BigDecimal("25.00"), 2, peca);

        assertEquals(new BigDecimal("50.00"), item.getValorTotal());
        assertEquals(TipoItemOrdemServico.PECA, item.getTipo());
        assertEquals(2, item.getQuantidade());
        assertEquals(peca, item.getPeca());
    }
}
