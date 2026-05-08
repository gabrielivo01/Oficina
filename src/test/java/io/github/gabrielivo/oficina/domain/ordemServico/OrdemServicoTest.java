package io.github.gabrielivo.oficina.domain.ordemServico;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrdemServicoTest {

    @Test
    void deveCriarOrdemServicoComStatusRecebidaETotalZero() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        assertNotNull(os.getId());
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());
        assertEquals(BigDecimal.ZERO, os.getValorTotal());
        assertTrue(os.getItens().isEmpty());
    }

    @Test
    void deveAdicionarItemERecalcularTotal() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        ItemOrdemServico item = new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("100.00"));
        os.adicionarItem(item);

        assertEquals(1, os.getItens().size());
        assertEquals(new BigDecimal("100.00"), os.getValorTotal());
    }

    @Test
    void deveRemoverItemERecalcularTotal() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        ItemOrdemServico item = new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("100.00"));
        os.adicionarItem(item);
        os.removerItem(item.getId());

        assertTrue(os.getItens().isEmpty());
        assertEquals(BigDecimal.ZERO, os.getValorTotal());
    }

    @Test
    void deveLancarExcecaoAoRemoverItemInexistente() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        OrdemServicoException exception = assertThrows(OrdemServicoException.class, () ->
            os.removerItem("item-inexistente")
        );

        assertEquals("Item não encontrado na OS: item-inexistente", exception.getMessage());
    }

    @Test
    void deveAvancarStatusQuandoTransicaoValida() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        os.avancarStatus();

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoParaTransicaoInvalida() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        os.avancarStatus();
        os.avancarStatus();
        os.avancarStatus();
        os.avancarStatus();
        os.avancarStatus();

        OrdemServicoException exception = assertThrows(OrdemServicoException.class, os::avancarStatus);

        assertTrue(exception.getMessage().contains("Transição inválida"));
    }
}
