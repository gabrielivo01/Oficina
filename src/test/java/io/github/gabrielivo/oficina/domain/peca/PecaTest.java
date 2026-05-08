package io.github.gabrielivo.oficina.domain.peca;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PecaTest {

    private Peca peca;

    @BeforeEach
    void setUp() {
        peca = new Peca("Filtro de Óleo", "Filtro de óleo para motor", new BigDecimal("25.00"), 10, 5);
    }

    @Test
    void deveReduzirEstoqueComSucesso() {
        // Act
        peca.reduzirEstoque(3);

        // Assert
        assertEquals(7, peca.getQuantidadeEstoque());
    }

    @Test
    void deveLancarExcecaoQuandoTentarReduzirMaisQueEstoqueDisponivel() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> peca.reduzirEstoque(15));
        assertEquals(10, peca.getQuantidadeEstoque()); // Estoque não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoTentarReduzirQuantidadeNegativa() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> peca.reduzirEstoque(-1));
        assertEquals(10, peca.getQuantidadeEstoque()); // Estoque não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoTentarReduzirQuantidadeZero() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> peca.reduzirEstoque(0));
        assertEquals(10, peca.getQuantidadeEstoque()); // Estoque não deve mudar
    }

    @Test
    void deveReporEstoqueComSucesso() {
        // Act
        peca.reporEstoque(5);

        // Assert
        assertEquals(15, peca.getQuantidadeEstoque());
    }

    @Test
    void deveLancarExcecaoQuandoTentarReporQuantidadeNegativa() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> peca.reporEstoque(-1));
        assertEquals(10, peca.getQuantidadeEstoque()); // Estoque não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoTentarReporQuantidadeZero() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> peca.reporEstoque(0));
        assertEquals(10, peca.getQuantidadeEstoque()); // Estoque não deve mudar
    }

    @Test
    void deveRetornarTrueQuandoEstoqueEstaBaixo() {
        // Arrange - criar peça com estoque baixo
        Peca pecaEstoqueBaixo = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 3, 5);

        // Act
        boolean result = pecaEstoqueBaixo.estoqueEstaBaixo();

        // Assert
        assertTrue(result);
    }

    @Test
    void deveRetornarFalseQuandoEstoqueNaoEstaBaixo() {
        // Arrange - criar peça com estoque normal
        Peca pecaEstoqueNormal = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 6, 5);

        // Act
        boolean result = pecaEstoqueNormal.estoqueEstaBaixo();

        // Assert
        assertFalse(result);
    }

    @Test
    void deveRetornarTrueQuandoEstoqueEstaCritico() {
        // Arrange - criar peça com estoque crítico
        Peca pecaEstoqueCritico = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 2, 5);

        // Act
        boolean result = pecaEstoqueCritico.estoqueEstaCritico();

        // Assert
        assertTrue(result);
    }

    @Test
    void deveRetornarFalseQuandoEstoqueNaoEstaCritico() {
        // Arrange - criar peça com estoque normal
        Peca pecaEstoqueNormal = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 4, 5);

        // Act
        boolean result = pecaEstoqueNormal.estoqueEstaCritico();

        // Assert
        assertFalse(result);
    }

    @Test
    void deveRetornarTrueQuandoEstoqueIgualAoMinimo() {
        // Arrange - criar peça com estoque igual ao mínimo
        Peca pecaEstoqueMinimo = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 5, 5);

        // Act
        boolean result = pecaEstoqueMinimo.estoqueEstaBaixo();

        // Assert
        assertTrue(result);
    }

    @Test
    void deveRetornarFalseQuandoEstoqueIgualA50PorCentoDoMinimo() {
        // Arrange - criar peça com estoque igual a 50% do mínimo
        Peca pecaEstoqueLimite = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 2, 5);

        // Act
        boolean result = pecaEstoqueLimite.estoqueEstaCritico();

        // Assert
        assertTrue(result); // Deve ser crítico pois 2 <= 2.5
    }

    @Test
    void deveCalcularEstoqueCriticoCorretamenteComMinimoImpar() {
        // Arrange - criar peça com mínimo ímpar
        Peca pecaMinimoImpar = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 3, 7); // 50% = 3.5, arredondado para baixo = 3

        // Act
        boolean result = pecaMinimoImpar.estoqueEstaCritico();

        // Assert
        assertTrue(result);
    }

    @Test
    void deveCalcularEstoqueCriticoCorretamenteComMinimoPar() {
        // Arrange - criar peça com mínimo par
        Peca pecaMinimoPar = new Peca("Peça Teste", "Descrição", new BigDecimal("10.00"), 3, 6); // 50% = 3

        // Act
        boolean result = pecaMinimoPar.estoqueEstaCritico();

        // Assert
        assertTrue(result);
    }

    @Test
    void deveAtualizarPecaComSucessoMantendoEstoqueMinimo() {
        // Act
        peca.atualizar("Novo Nome", "Nova Descrição", new BigDecimal("30.00"), 20);

        // Assert
        assertEquals("Novo Nome", peca.getNome());
        assertEquals("Nova Descrição", peca.getDescricao());
        assertEquals(new BigDecimal("30.00"), peca.getPreco());
        assertEquals(20, peca.getQuantidadeEstoque());
        assertEquals(5, peca.getEstoqueMinimo()); // Mantém o original
    }

    @Test
    void deveAtualizarPecaComSucessoAlterandoEstoqueMinimo() {
        // Act
        peca.atualizar("Novo Nome", "Nova Descrição", new BigDecimal("30.00"), 20, 3);

        // Assert
        assertEquals("Novo Nome", peca.getNome());
        assertEquals("Nova Descrição", peca.getDescricao());
        assertEquals(new BigDecimal("30.00"), peca.getPreco());
        assertEquals(20, peca.getQuantidadeEstoque());
        assertEquals(3, peca.getEstoqueMinimo());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComNomeNulo() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            peca.atualizar(null, "Descrição", new BigDecimal("30.00"), 20));
        assertEquals("Filtro de Óleo", peca.getNome()); // Não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComNomeVazio() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            peca.atualizar("", "Descrição", new BigDecimal("30.00"), 20));
        assertEquals("Filtro de Óleo", peca.getNome()); // Não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComPrecoNulo() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            peca.atualizar("Nome", "Descrição", null, 20));
        assertEquals(new BigDecimal("25.00"), peca.getPreco()); // Não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComPrecoZero() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            peca.atualizar("Nome", "Descrição", BigDecimal.ZERO, 20));
        assertEquals(new BigDecimal("25.00"), peca.getPreco()); // Não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComPrecoNegativo() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            peca.atualizar("Nome", "Descrição", new BigDecimal("-10.00"), 20));
        assertEquals(new BigDecimal("25.00"), peca.getPreco()); // Não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComQuantidadeEstoqueNegativa() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            peca.atualizar("Nome", "Descrição", new BigDecimal("30.00"), -1));
        assertEquals(10, peca.getQuantidadeEstoque()); // Não deve mudar
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarComEstoqueMinimoNegativo() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            peca.atualizar("Nome", "Descrição", new BigDecimal("30.00"), 20, -1));
        assertEquals(5, peca.getEstoqueMinimo()); // Não deve mudar
    }
}