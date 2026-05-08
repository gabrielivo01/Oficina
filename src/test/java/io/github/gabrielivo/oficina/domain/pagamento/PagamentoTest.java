package io.github.gabrielivo.oficina.domain.pagamento;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PagamentoTest {

    @Test
    void deveCriarPagamentoComDadosValidos() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        Pagamento pagamento = new Pagamento(os, new BigDecimal("250.00"), FormaPagamento.CARTAO);

        assertNotNull(pagamento.getId());
        assertEquals(os, pagamento.getOrdemServico());
        assertEquals(new BigDecimal("250.00"), pagamento.getValor());
        assertEquals(FormaPagamento.CARTAO, pagamento.getFormaPagamento());
    }

    @Test
    void deveLancarExcecaoQuandoValorIgualZero() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new Pagamento(os, BigDecimal.ZERO, FormaPagamento.DINHEIRO)
        );

        assertEquals("Valor do pagamento deve ser maior que zero.", exception.getMessage());
    }
}
