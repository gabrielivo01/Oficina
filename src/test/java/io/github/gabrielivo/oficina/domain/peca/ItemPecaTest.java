package io.github.gabrielivo.oficina.domain.peca;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;

@ExtendWith(MockitoExtension.class)
class ItemPecaTest {

    @Mock
    private OrdemServico ordemServico;

    @Mock
    private Peca peca;

    @Test
    void testConstrutor_Valido() {
        // Arrange
        when(peca.getPreco()).thenReturn(new BigDecimal("10.00"));
        Integer quantidade = 2;

        // Act
        ItemPeca item = new ItemPeca(ordemServico, peca, quantidade);

        // Assert
        assertNotNull(item.getId());
        assertEquals(ordemServico, item.getOrdemServico());
        assertEquals(peca, item.getPeca());
        assertEquals(quantidade, item.getQuantidade());
        assertEquals(new BigDecimal("20.00"), item.getValor()); // 10.00 * 2
    }

    @Test
    void testConstrutor_OrdemServicoNulo_LancaExcecao() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ItemPeca(null, peca, 1));
        assertEquals("Ordem de serviço é obrigatória.", exception.getMessage());
    }

    @Test
    void testConstrutor_PecaNula_LancaExcecao() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ItemPeca(ordemServico, null, 1));
        assertEquals("Peça é obrigatória.", exception.getMessage());
    }

    @Test
    void testConstrutor_QuantidadeNula_LancaExcecao() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ItemPeca(ordemServico, peca, null));
        assertEquals("Quantidade deve ser maior que zero.", exception.getMessage());
    }

    @Test
    void testConstrutor_QuantidadeZero_LancaExcecao() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ItemPeca(ordemServico, peca, 0));
        assertEquals("Quantidade deve ser maior que zero.", exception.getMessage());
    }

    @Test
    void testConstrutor_QuantidadeNegativa_LancaExcecao() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ItemPeca(ordemServico, peca, -1));
        assertEquals("Quantidade deve ser maior que zero.", exception.getMessage());
    }

    @Test
    void testOnCreate_IdNulo_DefineId() {
        // Arrange
        ItemPeca item = new ItemPeca();
        assertNull(item.getId());

        // Act
        item.onCreate();

        // Assert
        assertNotNull(item.getId());
    }
}