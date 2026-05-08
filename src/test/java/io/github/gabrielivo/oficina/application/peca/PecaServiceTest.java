/* package io.github.gabrielivo.oficina.application.peca;

import io.github.gabrielivo.oficina.application.ordemServico.PecaService;
import io.github.gabrielivo.oficina.domain.peca.Peca;
import io.github.gabrielivo.oficina.domain.peca.PecaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PecaServiceTest {

    @Mock
    private PecaRepository pecaRepository;

    @InjectMocks
    private PecaService pecaService;

    private Peca peca;
    private String pecaId;

    @BeforeEach
    void setUp() {
        pecaId = UUID.randomUUID().toString();
        peca = mock(Peca.class);
        when(peca.getId()).thenReturn(pecaId);
        when(peca.getNome()).thenReturn("Filtro de Óleo");
        when(peca.getDescricao()).thenReturn("Filtro de óleo para motor");
        when(peca.getPreco()).thenReturn(new BigDecimal("25.00"));
        when(peca.getQuantidadeEstoque()).thenReturn(10);
        when(peca.getEstoqueMinimo()).thenReturn(5);
    }

    @Test
    void deveCriarPecaComSucesso() {
        // Arrange
        var command = new CriarPecaCommand(
            "Filtro de Óleo",
            "Filtro de óleo para motor",
            new BigDecimal("25.00"),
            10,
            5
        );

        when(pecaRepository.save(any(Peca.class))).thenReturn(peca);

        // Act
        Peca result = pecaService.criar(command);

        // Assert
        assertNotNull(result);
        assertEquals("Filtro de Óleo", result.getNome());
        assertEquals("Filtro de óleo para motor", result.getDescricao());
        assertEquals(new BigDecimal("25.00"), result.getPreco());
        assertEquals(10, result.getQuantidadeEstoque());
        assertEquals(5, result.getEstoqueMinimo());
        verify(pecaRepository).save(any(Peca.class));
    }

    @Test
    void deveBuscarPecaPorId() {
        // Arrange
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));

        // Act
        Peca result = pecaService.buscarPorId(pecaId);

        // Assert
        assertNotNull(result);
        assertEquals(pecaId, result.getId());
        assertEquals("Filtro de Óleo", result.getNome());
        verify(pecaRepository).findById(pecaId);
    }

    @Test
    void deveLancarExcecaoQuandoPecaNaoEncontrada() {
        // Arrange
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> pecaService.buscarPorId(pecaId));
        verify(pecaRepository).findById(pecaId);
    }

    @Test
    void deveListarTodasPecas() {
        // Arrange
        List<Peca> pecas = List.of(peca);
        when(pecaRepository.findAll()).thenReturn(pecas);

        // Act
        List<Peca> result = pecaService.listarTodas();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(pecaId, result.get(0).getId());
        verify(pecaRepository).findAll();
    }

    @Test
    void deveAtualizarPecaComSucesso() {
        // Arrange
        var command = new AtualizarPecaCommand(
            "Filtro Atualizado",
            "Descrição atualizada",
            new BigDecimal("30.00"),
            15,
            3
        );

        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));
        when(pecaRepository.save(any(Peca.class))).thenReturn(peca);

        // Act
        Peca result = pecaService.atualizar(pecaId, command);

        // Assert
        assertNotNull(result);
        verify(pecaRepository).findById(pecaId);
        verify(pecaRepository).save(peca);
    }

    @Test
    void deveDeletarPecaComSucesso() {
        // Arrange
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));

        // Act
        pecaService.deletar(pecaId);

        // Assert
        verify(pecaRepository).findById(pecaId);
        verify(pecaRepository).delete(peca);
    }

    @Test
    void deveReporEstoqueComSucesso() {
        // Arrange
        int quantidadeReposicao = 5;
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));
        when(pecaRepository.save(any(Peca.class))).thenReturn(peca);

        // Act
        Peca result = pecaService.reporEstoque(pecaId, quantidadeReposicao);

        // Assert
        assertNotNull(result);
        assertEquals(15, peca.getQuantidadeEstoque()); // 10 + 5
        verify(pecaRepository).findById(pecaId);
        verify(pecaRepository).save(peca);
    }

    @Test
    void deveListarPecasComEstoqueBaixo() {
        // Arrange
        Peca pecaEstoqueBaixo = new Peca();
        pecaEstoqueBaixo.setId(UUID.randomUUID().toString());
        pecaEstoqueBaixo.setNome("Peça Estoque Baixo");
        pecaEstoqueBaixo.setQuantidadeEstoque(3);
        pecaEstoqueBaixo.setEstoqueMinimo(5);

        Peca pecaEstoqueNormal = new Peca();
        pecaEstoqueNormal.setId(UUID.randomUUID().toString());
        pecaEstoqueNormal.setNome("Peça Estoque Normal");
        pecaEstoqueNormal.setQuantidadeEstoque(10);
        pecaEstoqueNormal.setEstoqueMinimo(5);

        List<Peca> todasPecas = List.of(pecaEstoqueBaixo, pecaEstoqueNormal);
        when(pecaRepository.findAll()).thenReturn(todasPecas);

        // Act
        List<Peca> result = pecaService.listarPecasComEstoqueBaixo();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Peça Estoque Baixo", result.get(0).getNome());
        assertEquals(3, result.get(0).getQuantidadeEstoque());
        assertEquals(5, result.get(0).getEstoqueMinimo());
        verify(pecaRepository).findAll();
    }

    @Test
    void deveListarPecasComEstoqueCritico() {
        // Arrange
        Peca pecaEstoqueCritico = new Peca();
        pecaEstoqueCritico.setId(UUID.randomUUID().toString());
        pecaEstoqueCritico.setNome("Peça Estoque Crítico");
        pecaEstoqueCritico.setQuantidadeEstoque(1);
        pecaEstoqueCritico.setEstoqueMinimo(5);

        Peca pecaEstoqueNormal = new Peca();
        pecaEstoqueNormal.setId(UUID.randomUUID().toString());
        pecaEstoqueNormal.setNome("Peça Estoque Normal");
        pecaEstoqueNormal.setQuantidadeEstoque(10);
        pecaEstoqueNormal.setEstoqueMinimo(5);

        List<Peca> todasPecas = List.of(pecaEstoqueCritico, pecaEstoqueNormal);
        when(pecaRepository.findAll()).thenReturn(todasPecas);

        // Act
        List<Peca> result = pecaService.listarPecasComEstoqueCritico();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Peça Estoque Crítico", result.get(0).getNome());
        assertEquals(1, result.get(0).getQuantidadeEstoque());
        assertEquals(5, result.get(0).getEstoqueMinimo());
        verify(pecaRepository).findAll();
    }

    @Test
    void deveRetornarListaVaziaQuandoNenhumaPecaComEstoqueBaixo() {
        // Arrange
        Peca pecaEstoqueNormal = new Peca();
        pecaEstoqueNormal.setId(UUID.randomUUID().toString());
        pecaEstoqueNormal.setNome("Peça Estoque Normal");
        pecaEstoqueNormal.setQuantidadeEstoque(10);
        pecaEstoqueNormal.setEstoqueMinimo(5);

        List<Peca> todasPecas = List.of(pecaEstoqueNormal);
        when(pecaRepository.findAll()).thenReturn(todasPecas);

        // Act
        List<Peca> result = pecaService.listarPecasComEstoqueBaixo();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(pecaRepository).findAll();
    }

    @Test
    void deveRetornarListaVaziaQuandoNenhumaPecaComEstoqueCritico() {
        // Arrange
        Peca pecaEstoqueNormal = new Peca();
        pecaEstoqueNormal.setId(UUID.randomUUID().toString());
        pecaEstoqueNormal.setNome("Peça Estoque Normal");
        pecaEstoqueNormal.setQuantidadeEstoque(10);
        pecaEstoqueNormal.setEstoqueMinimo(5);

        List<Peca> todasPecas = List.of(pecaEstoqueNormal);
        when(pecaRepository.findAll()).thenReturn(todasPecas);

        // Act
        List<Peca> result = pecaService.listarPecasComEstoqueCritico();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(pecaRepository).findAll();
    }
} */