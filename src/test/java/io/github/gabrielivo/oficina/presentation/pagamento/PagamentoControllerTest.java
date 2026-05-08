package io.github.gabrielivo.oficina.presentation.pagamento;

import io.github.gabrielivo.oficina.application.pagamento.PagamentoService;
import io.github.gabrielivo.oficina.application.pagamento.RegistrarPagamentoCommand;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;
import io.github.gabrielivo.oficina.domain.pagamento.Pagamento;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagamentoControllerTest {

    @Mock
    private PagamentoService pagamentoService;

    @Mock
    private PagamentoMapper pagamentoMapper;

    @InjectMocks
    private PagamentoController pagamentoController;

    @Test
    void deveRegistrarPagamentoERetornarCreated() {
        String ordemServicoId = "os-id";
        PagamentoRequest request = new PagamentoRequest(new BigDecimal("150.00"), FormaPagamento.DINHEIRO);
        RegistrarPagamentoCommand command = new RegistrarPagamentoCommand(ordemServicoId, request.valor(), request.formaPagamento());
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico ordemServico = new OrdemServico(cliente, veiculo);
        Pagamento pagamento = new Pagamento(ordemServico, new BigDecimal("150.00"), FormaPagamento.DINHEIRO);
        PagamentoResponse responseBody = new PagamentoResponse(pagamento.getId(), ordemServico.getId(), pagamento.getValor(), pagamento.getFormaPagamento(), pagamento.getCriadoEm());

        when(pagamentoMapper.toCommand(ordemServicoId, request)).thenReturn(command);
        when(pagamentoService.registrar(command)).thenReturn(pagamento);
        when(pagamentoMapper.toResponse(pagamento)).thenReturn(responseBody);

        ResponseEntity<PagamentoResponse> response = pagamentoController.registrar(ordemServicoId, request);

        assertEquals(201, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }

    @Test
    void deveListarPagamentosPorOrdemServicoERetornarOk() {
        String ordemServicoId = "os-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico ordemServico = new OrdemServico(cliente, veiculo);
        Pagamento pagamento = new Pagamento(ordemServico, new BigDecimal("150.00"), FormaPagamento.CARTAO);
        PagamentoResponse responseBody = new PagamentoResponse(pagamento.getId(), ordemServico.getId(), pagamento.getValor(), pagamento.getFormaPagamento(), pagamento.getCriadoEm());

        when(pagamentoService.listarPorOrdemServico(ordemServicoId)).thenReturn(List.of(pagamento));
        when(pagamentoMapper.toResponse(pagamento)).thenReturn(responseBody);

        ResponseEntity<List<PagamentoResponse>> response = pagamentoController.listarPorOrdemServico(ordemServicoId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertSame(responseBody, response.getBody().get(0));
    }

    @Test
    void deveBuscarPagamentoPorIdERetornarOk() {
        String id = "pagamento-id";
        Cliente cliente = new Cliente("11122233344", "Gabriel", "61999999999", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico ordemServico = new OrdemServico(cliente, veiculo);
        Pagamento pagamento = new Pagamento(ordemServico, new BigDecimal("150.00"), FormaPagamento.DINHEIRO);
        PagamentoResponse responseBody = new PagamentoResponse(pagamento.getId(), ordemServico.getId(), pagamento.getValor(), pagamento.getFormaPagamento(), pagamento.getCriadoEm());

        when(pagamentoService.buscarPorId(id)).thenReturn(pagamento);
        when(pagamentoMapper.toResponse(pagamento)).thenReturn(responseBody);

        ResponseEntity<PagamentoResponse> response = pagamentoController.buscarPorId(id);

        assertEquals(200, response.getStatusCode().value());
        assertSame(responseBody, response.getBody());
    }
}
