package io.github.gabrielivo.oficina.domain.ordemServico;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

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
    void deveManterSnapshotDePecaSemDependenciaDoCatalogo() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);
        ItemOrdemServico item =
                new ItemOrdemServico(os, "Filtro de óleo", new BigDecimal("25.00"), 2, "peca-1", "Filtro de óleo");

        assertEquals(new BigDecimal("50.00"), item.getValorTotal());
        assertEquals(TipoItemOrdemServico.PECA, item.getTipo());
        assertEquals(2, item.getQuantidade());
        assertEquals("peca-1", item.getPecaId());
        assertEquals("Filtro de óleo", item.getNomePeca());
    }

    @Test
    void deveExigirNomeDaPecaParaManterSnapshot() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ItemOrdemServico(os, "Filtro de óleo", new BigDecimal("25.00"), 1, "peca-1", " "));
    }
}
