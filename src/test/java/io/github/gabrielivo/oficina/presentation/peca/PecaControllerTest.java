package io.github.gabrielivo.oficina.presentation.peca;

import io.github.gabrielivo.oficina.application.ordemServico.PecaService;
import io.github.gabrielivo.oficina.domain.peca.Peca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PecaControllerTest {

    @Mock
    private PecaService pecaService;

    @InjectMocks
    private PecaController pecaController;

    private Peca peca;
    private String pecaId;

    @BeforeEach
    void setUp() {
        pecaId = UUID.randomUUID().toString();
        peca = new Peca("Filtro de Óleo", "Filtro de óleo para motor", new BigDecimal("25.00"), 10, 5);
        // Usar reflexão para setar o ID
        try {
            var field = Peca.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(peca, pecaId);
        } catch (Exception e) {
            // Ignorar para testes
        }
    }

    @Test
    void deveCriarPecaComSucesso() {
        // Arrange
        var request = new CriarPecaRequest(
            "Filtro de Óleo",
            "Filtro de óleo para motor",
            new BigDecimal("25.00"),
            10,
            5
        );

        when(pecaService.criar(any())).thenReturn(peca);

        // Act
        ResponseEntity<Peca> response = pecaController.criar(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(pecaId, response.getBody().getId());
        verify(pecaService).criar(any());
    }

    @Test
    void deveListarTodasPecas() {
        // Arrange
        List<Peca> pecas = List.of(peca);
        when(pecaService.listarTodas()).thenReturn(pecas);

        // Act
        ResponseEntity<List<Peca>> response = pecaController.listar();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(pecaId, response.getBody().get(0).getId());
        verify(pecaService).listarTodas();
    }

    @Test
    void deveBuscarPecaPorId() {
        // Arrange
        when(pecaService.buscarPorId(pecaId)).thenReturn(peca);

        // Act
        ResponseEntity<Peca> response = pecaController.buscar(pecaId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(pecaId, response.getBody().getId());
        verify(pecaService).buscarPorId(pecaId);
    }

    @Test
    void deveAtualizarPecaComSucesso() {
        // Arrange
        var request = new AtualizarPecaRequest(
            "Filtro Atualizado",
            "Descrição atualizada",
            new BigDecimal("30.00"),
            15,
            3
        );

        when(pecaService.atualizar(eq(pecaId), any())).thenReturn(peca);

        // Act
        ResponseEntity<Peca> response = pecaController.atualizar(pecaId, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(pecaService).atualizar(eq(pecaId), any());
    }

    @Test
    void deveDeletarPecaComSucesso() {
        // Act
        ResponseEntity<Void> response = pecaController.deletar(pecaId);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(pecaService).deletar(pecaId);
    }

    @Test
    void deveReporEstoqueComSucesso() {
        // Arrange
        int quantidade = 5;
        when(pecaService.reporEstoque(pecaId, quantidade)).thenReturn(peca);

        // Act
        ResponseEntity<Peca> response = pecaController.reporEstoque(pecaId, quantidade);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(pecaService).reporEstoque(pecaId, quantidade);
    }

    @Test
    void deveListarAlertasEstoqueBaixo() {
        // Arrange
        List<Peca> pecasEstoqueBaixo = List.of(peca);
        when(pecaService.listarPecasComEstoqueBaixo()).thenReturn(pecasEstoqueBaixo);

        // Act
        ResponseEntity<List<Peca>> response = pecaController.alertasEstoqueBaixo();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(pecaService).listarPecasComEstoqueBaixo();
    }

    @Test
    void deveListarAlertasEstoqueCritico() {
        // Arrange
        List<Peca> pecasEstoqueCritico = List.of(peca);
        when(pecaService.listarPecasComEstoqueCritico()).thenReturn(pecasEstoqueCritico);

        // Act
        ResponseEntity<List<Peca>> response = pecaController.alertasEstoqueCritico();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(pecaService).listarPecasComEstoqueCritico();
    }
}