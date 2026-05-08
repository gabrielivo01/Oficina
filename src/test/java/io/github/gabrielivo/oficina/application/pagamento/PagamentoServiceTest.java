package io.github.gabrielivo.oficina.application.pagamento;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoException;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;
import io.github.gabrielivo.oficina.domain.pagamento.Pagamento;
import io.github.gabrielivo.oficina.domain.pagamento.PagamentoException;
import io.github.gabrielivo.oficina.domain.pagamento.PagamentoRepository;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @InjectMocks
    private PagamentoService pagamentoService;

    private OrdemServico ordemServico;

    @BeforeEach
    void setup() {
        Cliente cliente = new Cliente("11122233344", "Cliente Teste", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        ordemServico = new OrdemServico(cliente, veiculo);
    }

    @Test
    void deveRegistrarPagamentoQuandoDadosSaoValidos() {
        var command = new RegistrarPagamentoCommand(ordemServico.getId(), new BigDecimal("150.00"), FormaPagamento.PIX);

        when(ordemServicoRepository.findById(ordemServico.getId()))
            .thenReturn(Optional.of(ordemServico));
        when(pagamentoRepository.existsByOrdemServicoId(ordemServico.getId()))
            .thenReturn(false);
        when(pagamentoRepository.save(any(Pagamento.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Pagamento pagamento = pagamentoService.registrar(command);

        assertNotNull(pagamento.getId());
        assertEquals(ordemServico.getId(), pagamento.getOrdemServico().getId());
        assertEquals(new BigDecimal("150.00"), pagamento.getValor());
        assertEquals(FormaPagamento.PIX, pagamento.getFormaPagamento());

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepository).save(captor.capture());
        assertEquals(ordemServico.getId(), captor.getValue().getOrdemServico().getId());
    }

    @Test
    void deveLancarExcecaoQuandoRegistrarPagamentoComOSInexistente() {
        String osId = "os-inexistente";
        var command = new RegistrarPagamentoCommand(osId, new BigDecimal("150.00"), FormaPagamento.PIX);

        when(ordemServicoRepository.findById(osId)).thenReturn(Optional.empty());

        OrdemServicoException exception = assertThrows(OrdemServicoException.class, () -> pagamentoService.registrar(command));
        assertEquals("Ordem de Serviço não encontrada: " + osId, exception.getMessage());

        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoJaRegistradoParaMesmaOS() {
        var command = new RegistrarPagamentoCommand(ordemServico.getId(), new BigDecimal("200.00"), FormaPagamento.CARTAO);

        when(ordemServicoRepository.findById(ordemServico.getId()))
            .thenReturn(Optional.of(ordemServico));
        when(pagamentoRepository.existsByOrdemServicoId(ordemServico.getId()))
            .thenReturn(true);

        PagamentoException exception = assertThrows(PagamentoException.class, () -> pagamentoService.registrar(command));
        assertEquals("Esta OS já possui um pagamento registrado.", exception.getMessage());

        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    void deveBuscarPagamentoPorIdQuandoExiste() {
        Pagamento pagamento = new Pagamento(ordemServico, new BigDecimal("175.00"), FormaPagamento.PIX);
        when(pagamentoRepository.findById(pagamento.getId())).thenReturn(Optional.of(pagamento));

        Pagamento resultado = pagamentoService.buscarPorId(pagamento.getId());

        assertSame(pagamento, resultado);
    }

    @Test
    void deveLancarExcecaoQuandoBuscarPagamentoPorIdInexistente() {
        String idInexistente = "pagamento-inexistente";
        when(pagamentoRepository.findById(idInexistente)).thenReturn(Optional.empty());

        PagamentoException exception = assertThrows(PagamentoException.class, () -> pagamentoService.buscarPorId(idInexistente));
        assertEquals("Pagamento não encontrado: " + idInexistente, exception.getMessage());
    }

    @Test
    void deveListarPagamentosPorOrdemServico() {
        Pagamento pagamento1 = new Pagamento(ordemServico, new BigDecimal("100.00"), FormaPagamento.PIX);
        Pagamento pagamento2 = new Pagamento(ordemServico, new BigDecimal("50.00"), FormaPagamento.DINHEIRO);

        when(pagamentoRepository.findByOrdemServicoId(ordemServico.getId()))
            .thenReturn(List.of(pagamento1, pagamento2));

        List<Pagamento> pagamentos = pagamentoService.listarPorOrdemServico(ordemServico.getId());

        assertEquals(2, pagamentos.size());
        assertEquals(ordemServico.getId(), pagamentos.get(0).getOrdemServico().getId());
        assertEquals(ordemServico.getId(), pagamentos.get(1).getOrdemServico().getId());
    }
}
