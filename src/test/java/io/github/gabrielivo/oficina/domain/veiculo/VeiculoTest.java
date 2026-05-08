package io.github.gabrielivo.oficina.domain.veiculo;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VeiculoTest {

    @Test
    void deveCriarVeiculoComDadosValidos() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);

        assertNotNull(veiculo.getId());
        assertEquals(cliente, veiculo.getCliente());
        assertEquals("ABC1234", veiculo.getPlaca());
        assertEquals("Fiat", veiculo.getMarca());
        assertEquals("Uno", veiculo.getModelo());
        assertEquals(2020, veiculo.getAno());
    }

    @Test
    void deveLancarExcecaoQuandoPlacaForVazia() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new Veiculo(cliente, "", "Fiat", "Uno", 2020)
        );

        assertEquals("Placa é obrigatória.", exception.getMessage());
    }

    @Test
    void deveAtualizarVeiculoComDadosValidos() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);

        veiculo.atualizar("Volkswagen", "Gol", 2022);

        assertEquals("Volkswagen", veiculo.getMarca());
        assertEquals("Gol", veiculo.getModelo());
        assertEquals(2022, veiculo.getAno());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComMarcaNula() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            veiculo.atualizar(null, "Gol", 2022)
        );

        assertEquals("Marca é obrigatória.", exception.getMessage());
    }
}
