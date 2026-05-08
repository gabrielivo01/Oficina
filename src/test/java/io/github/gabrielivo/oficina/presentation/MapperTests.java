package io.github.gabrielivo.oficina.presentation;


import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.Endereco;
import io.github.gabrielivo.oficina.domain.ordemServico.ItemOrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.presentation.cliente.ClienteMapper;
import io.github.gabrielivo.oficina.presentation.cliente.ClienteRequest;
import io.github.gabrielivo.oficina.presentation.cliente.EnderecoRequest;
import io.github.gabrielivo.oficina.presentation.ordemServico.ItemOSResponse;
import io.github.gabrielivo.oficina.presentation.ordemServico.OrdemServicoMapper;
import io.github.gabrielivo.oficina.presentation.ordemServico.OrdemServicoRequest;
import io.github.gabrielivo.oficina.presentation.pagamento.PagamentoMapper;
import io.github.gabrielivo.oficina.presentation.pagamento.PagamentoRequest;
import io.github.gabrielivo.oficina.presentation.veiculo.VeiculoMapper;
import io.github.gabrielivo.oficina.presentation.veiculo.VeiculoRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MapperTests {

    private final ClienteMapper clienteMapper = new ClienteMapper();
    private final VeiculoMapper veiculoMapper = new VeiculoMapper();
    private final PagamentoMapper pagamentoMapper = new PagamentoMapper();
    private final OrdemServicoMapper ordemServicoMapper = new OrdemServicoMapper();

    @Test
    void deveMapearClienteRequestParaCriarClienteCommand() {
        var enderecoRequest = new EnderecoRequest(
            "12345678", "Rua Teste", "100", "Apto 1",
            "Centro", "Cidade", "SP");

        var request = new ClienteRequest("11122233344", "Gabriel", "61900000000", enderecoRequest);
        var command = clienteMapper.toCommand(request);

        assertEquals("11122233344", command.cpf());
        assertEquals("Gabriel", command.nome());
        assertEquals("61900000000", command.telefone());
        assertNotNull(command.endereco());
        assertEquals("Rua Teste", command.endereco().logradouro());
    }

    @Test
    void deveMapearClienteParaClienteResponse() {
        var endereco = new Endereco("12345678", "Rua Teste", "100", "Apto 1", "Centro", "Cidade", "SP");
        var cliente = new Cliente("11122233344", "Gabriel", "61900000000", endereco);
        var response = clienteMapper.toResponse(cliente);

        assertEquals(cliente.getId(), response.id());
        assertEquals("Gabriel", response.nome());
        assertNotNull(response.endereco());
        assertEquals("Centro", response.endereco().bairro());
    }

    @Test
    void deveMapearVeiculoRequestParaCriarVeiculoCommand() {
        var request = new VeiculoRequest("cliente-1", "ABC1234", "Fiat", "Uno", 2020);
        var command = veiculoMapper.toCommand(request);

        assertEquals("cliente-1", command.clienteId());
        assertEquals("ABC1234", command.placa());
        assertEquals("Uno", command.modelo());
    }

    @Test
    void deveMapearVeiculoParaResponse() {
        var cliente = new Cliente("11122233344", "Gabriel", "61900000000", null);
        var veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        var response = veiculoMapper.toResponse(veiculo);

        assertEquals(veiculo.getId(), response.id());
        assertEquals("ABC1234", response.placa());
        assertEquals("Fiat", response.marca());
    }

    @Test
    void deveMapearPagamentoRequestParaRegistrarPagamentoCommand() {
        var request = new PagamentoRequest(new java.math.BigDecimal("150.00"), null);
        var command = pagamentoMapper.toCommand("os-1", request);

        assertEquals("os-1", command.ordemServicoId());
        assertEquals(new BigDecimal("150.00"), command.valor());
    }

    @Test
    void deveMapearPagamentoParaResponse() {
        var cliente = new Cliente("11122233344", "Gabriel", "61900000000", null);
        var veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        var ordemServico = new OrdemServico(cliente, veiculo);
        var pagamento = new io.github.gabrielivo.oficina.domain.pagamento.Pagamento(
                ordemServico, new BigDecimal("150.00"), io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento.DINHEIRO);

        var response = pagamentoMapper.toResponse(pagamento);

        assertEquals(pagamento.getId(), response.id());
        assertEquals(pagamento.getValor(), response.valor());
        assertEquals(pagamento.getFormaPagamento(), response.formaPagamento());
    }

    @Test
    void deveMapearOrdemServicoRequestParaAbrirCommand() {
        var request = new OrdemServicoRequest("cliente-1", "veiculo-1");
        var command = ordemServicoMapper.toCommand(request);

        assertEquals("cliente-1", command.clienteId());
        assertEquals("veiculo-1", command.veiculoId());
    }

    @Test
    void deveMapearOrdemServicoParaResponseComItens() {
        var cliente = new Cliente("11122233344", "Gabriel", "61900000000", null);
        var veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        var ordemServico = new OrdemServico(cliente, veiculo);
        var item = new ItemOrdemServico(ordemServico, "Troca de óleo", new BigDecimal("100.00"));
        ordemServico.adicionarItem(item);

        var response = ordemServicoMapper.toResponse(ordemServico);

        assertEquals(ordemServico.getId(), response.id());
        assertEquals(1, response.itens().size());
        ItemOSResponse itemResponse = response.itens().get(0);
        assertEquals("Troca de óleo", itemResponse.descricao());
    }
}
