package io.github.gabrielivo.oficina.application.ordemServico;

import io.github.gabrielivo.oficina.application.cliente.ClienteService;
import io.github.gabrielivo.oficina.application.cliente.CriarClienteCommand;
import io.github.gabrielivo.oficina.application.cliente.EnderecoCommand;
import io.github.gabrielivo.oficina.application.veiculo.CriarVeiculoCommand;
import io.github.gabrielivo.oficina.application.veiculo.VeiculoService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.*;
import io.github.gabrielivo.oficina.domain.peca.Peca;
import io.github.gabrielivo.oficina.domain.peca.PecaRepository;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({OrdemServicoService.class, ClienteService.class, VeiculoService.class, PecaService.class})
class OrdemServicoServiceTest {

    @Autowired
    private OrdemServicoRepository ordemServicoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private PecaRepository pecaRepository;

    @Autowired
    private OrdemServicoService ordemServicoService;

    @Autowired
    ClienteService clienteService;

    @Autowired
    VeiculoService veiculoService;

    @Autowired
    PecaService pecaService;

    private Cliente criarClienteTeste() {
        var endereco = new EnderecoCommand("12345678", "Rua Teste", "123", null, "Centro", "São Paulo", "SP");
        var cpf = "12345678901"; // CPF fixo para testes que precisam do mesmo cliente
        try {
            var command = new CriarClienteCommand(cpf, "Cliente Teste", "(11) 99999-9999", endereco);
            return clienteService.criar(command);
        } catch (Exception e) {
            // Cliente já existe, buscar pelo CPF
            return clienteRepository.findByCpf(cpf).orElseThrow();
        }
    }

    private Cliente criarClienteUnico() {
        var endereco = new EnderecoCommand("12345678", "Rua Teste", "123", null, "Centro", "São Paulo", "SP");
        var cpf = java.util.UUID.randomUUID().toString().substring(0, 11).replace("-", ""); // CPF único
        var command = new CriarClienteCommand(cpf, "Cliente " + cpf, "(11) 99999-9999", endereco);
        return clienteService.criar(command);
    }

    private Veiculo criarVeiculoTeste(String clienteId) {
        var placa = "ABC-1234"; // Placa fixa para testes que precisam do mesmo veículo
        if (veiculoRepository.existsByPlaca(placa)) {
            // Veículo já existe, buscar pelo cliente (assumindo que o cliente tem apenas um veículo)
            var veiculos = veiculoRepository.findByClienteId(clienteId);
            return veiculos.stream().filter(v -> v.getPlaca().equals(placa)).findFirst().orElseThrow();
        } else {
            var command = new CriarVeiculoCommand(clienteId, placa, "Fiat", "Uno", 2020);
            return veiculoService.criar(command);
        }
    }

    private Veiculo criarVeiculoUnico(String clienteId) {
        var placa = java.util.UUID.randomUUID().toString().substring(0, 7).toUpperCase(); // Placa única
        var command = new CriarVeiculoCommand(clienteId, placa, "Fiat", "Uno", 2020);
        return veiculoService.criar(command);
    }

    private Peca criarPecaTeste() {
        var command = new CriarPecaCommand("Peça Teste", "Descrição", new BigDecimal("100.00"), 10, 2);
        return pecaService.criar(command);
    }

    @Test
    void deveAbrirOrdemServicoQuandoClienteEVeiculoExistem() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());

        var command = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(command);

        assertNotNull(os.getId());
        assertEquals(cliente.getId(), os.getCliente().getId());
        assertEquals(veiculo.getId(), os.getVeiculo().getId());
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());
        assertNotNull(os.getId());
    }

    @Test
    void deveLancarExcecaoQuandoAbrirOrdemServicoComClienteInexistente() {
        String clienteIdInexistente = "cliente-inexistente";
        String veiculoId = "veiculo-qualquer";

        var command = new AbrirOrdemServicoCommand(clienteIdInexistente, veiculoId);

        assertThrows(io.github.gabrielivo.oficina.domain.cliente.ClienteException.class, () -> {
            ordemServicoService.abrir(command);
        });
    }

    @Test
    void deveLancarExcecaoQuandoAbrirOrdemServicoComVeiculoInexistente() {
        Cliente cliente = criarClienteTeste();
        String veiculoIdInexistente = "veiculo-inexistente";

        var command = new AbrirOrdemServicoCommand(cliente.getId(), veiculoIdInexistente);

        assertThrows(io.github.gabrielivo.oficina.domain.veiculo.VeiculoException.class, () -> {
            ordemServicoService.abrir(command);
        });
    }

    @Test
    void deveBuscarOrdemServicoPorIdQuandoExiste() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico osCriada = ordemServicoService.abrir(commandAbrir);

        OrdemServico osBuscada = ordemServicoService.buscarPorId(osCriada.getId());

        assertEquals(osCriada.getId(), osBuscada.getId());
        assertEquals(cliente.getId(), osBuscada.getCliente().getId());
        assertEquals(veiculo.getId(), osBuscada.getVeiculo().getId());
    }

    @Test
    void deveLancarExcecaoQuandoBuscarOrdemServicoPorIdInexistente() {
        String idInexistente = "id-inexistente";

        assertThrows(OrdemServicoException.class, () -> {
            ordemServicoService.buscarPorId(idInexistente);
        });
    }

    @Test
    void deveListarTodasAsOrdensServico() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());

        // Criar duas OS
        var command1 = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        ordemServicoService.abrir(command1);

        var command2 = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        ordemServicoService.abrir(command2);

        List<OrdemServico> ordens = ordemServicoService.listarTodas();

        assertNotNull(ordens);
        assertTrue(ordens.size() >= 2);
    }

    @Test
    void deveListarOrdensServicoPorCliente() {
        Cliente cliente1 = criarClienteUnico();
        Cliente cliente2 = criarClienteUnico();
        Veiculo veiculo1 = criarVeiculoUnico(cliente1.getId());
        Veiculo veiculo2 = criarVeiculoUnico(cliente2.getId());

        // Criar OS para cliente1
        var command1 = new AbrirOrdemServicoCommand(cliente1.getId(), veiculo1.getId());
        ordemServicoService.abrir(command1);

        // Criar OS para cliente2
        var command2 = new AbrirOrdemServicoCommand(cliente2.getId(), veiculo2.getId());
        ordemServicoService.abrir(command2);

        List<OrdemServico> ordensCliente1 = ordemServicoService.listarPorCliente(cliente1.getId());

        assertNotNull(ordensCliente1);
        assertEquals(1, ordensCliente1.size());
        assertEquals(cliente1.getId(), ordensCliente1.get(0).getCliente().getId());
    }

    @Test
    void deveListarOrdensServicoPorVeiculo() {
        Cliente cliente = criarClienteUnico();
        Veiculo veiculo1 = criarVeiculoUnico(cliente.getId());
        Veiculo veiculo2 = criarVeiculoUnico(cliente.getId());

        // Criar OS para veiculo1
        var command1 = new AbrirOrdemServicoCommand(cliente.getId(), veiculo1.getId());
        ordemServicoService.abrir(command1);

        // Criar OS para veiculo2
        var command2 = new AbrirOrdemServicoCommand(cliente.getId(), veiculo2.getId());
        ordemServicoService.abrir(command2);

        List<OrdemServico> ordensVeiculo1 = ordemServicoService.listarPorVeiculo(veiculo1.getId());

        assertNotNull(ordensVeiculo1);
        assertEquals(1, ordensVeiculo1.size());
        assertEquals(veiculo1.getId(), ordensVeiculo1.get(0).getVeiculo().getId());
    }

    @Test
    void deveAdicionarItemServico() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        var commandItem = new AdicionarItemOSCommand(os.getId(), "Troca de óleo", TipoItemOrdemServico.SERVICO, new BigDecimal("50.00"), null, null);
        OrdemServico osComItem = ordemServicoService.adicionarItem(commandItem);

        assertNotNull(osComItem.getItens());
        assertEquals(1, osComItem.getItens().size());
        var item = osComItem.getItens().get(0);
        assertEquals("Troca de óleo", item.getDescricao());
        assertEquals(new BigDecimal("50.00"), item.getValor());
        assertEquals(TipoItemOrdemServico.SERVICO, item.getTipo());
    }

    @Test
    void deveAdicionarItemPeca() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        Peca peca = criarPecaTeste();

        var commandItem = new AdicionarItemOSCommand(os.getId(), "Filtro de óleo", TipoItemOrdemServico.PECA, new BigDecimal("25.00"), 2, peca.getId());
        OrdemServico osComItem = ordemServicoService.adicionarItem(commandItem);

        assertNotNull(osComItem.getItens());
        assertEquals(1, osComItem.getItens().size());
        var item = osComItem.getItens().get(0);
        assertEquals("Filtro de óleo", item.getDescricao());
        assertEquals(new BigDecimal("25.00"), item.getValor());
        assertEquals(TipoItemOrdemServico.PECA, item.getTipo());
        assertEquals(2, item.getQuantidade());
        assertEquals(peca.getId(), item.getPeca().getId());

        // Verificar se estoque foi reduzido
        Optional<Peca> pecaAtualizada = pecaRepository.findById(peca.getId());
        assertTrue(pecaAtualizada.isPresent());
        assertEquals(8, pecaAtualizada.get().getQuantidadeEstoque()); // 10 - 2
    }

    @Test
    void deveLancarExcecaoQuandoAdicionarItemPecaSemPecaId() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        var commandItem = new AdicionarItemOSCommand(os.getId(), "Filtro", TipoItemOrdemServico.PECA, new BigDecimal("25.00"), 1, null);

        assertThrows(OrdemServicoException.class, () -> {
            ordemServicoService.adicionarItem(commandItem);
        });
    }

    @Test
    void deveRemoverItemDaOrdemServico() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        var commandItem = new AdicionarItemOSCommand(os.getId(), "Troca de óleo", TipoItemOrdemServico.SERVICO, new BigDecimal("50.00"), null, null);
        OrdemServico osComItem = ordemServicoService.adicionarItem(commandItem);

        String itemId = osComItem.getItens().get(0).getId();

        OrdemServico osSemItem = ordemServicoService.removerItem(os.getId(), itemId);

        assertNotNull(osSemItem.getItens());
        assertEquals(0, osSemItem.getItens().size());
    }

    @Test
    void deveAvancarStatusDaOrdemServico() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());

        OrdemServico osAvancada = ordemServicoService.avancarStatus(os.getId());

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, osAvancada.getStatus());
    }

    @Test
    void deveRestaurarEstoqueQuandoRemoverItemPeca() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        Peca peca = criarPecaTeste();
        int estoqueInicial = peca.getQuantidadeEstoque(); // 10

        // Adicionar item peça (reduz estoque)
        var commandItem = new AdicionarItemOSCommand(os.getId(), "Filtro de óleo", TipoItemOrdemServico.PECA, new BigDecimal("25.00"), 2, peca.getId());
        OrdemServico osComItem = ordemServicoService.adicionarItem(commandItem);

        // Verificar que estoque foi reduzido
        Optional<Peca> pecaAposAdicao = pecaRepository.findById(peca.getId());
        assertTrue(pecaAposAdicao.isPresent());
        assertEquals(estoqueInicial - 2, pecaAposAdicao.get().getQuantidadeEstoque()); // 8

        // Remover item peça (restaura estoque)
        String itemId = osComItem.getItens().get(0).getId();
        OrdemServico osSemItem = ordemServicoService.removerItem(os.getId(), itemId);

        // Verificar que estoque foi restaurado
        Optional<Peca> pecaAposRemocao = pecaRepository.findById(peca.getId());
        assertTrue(pecaAposRemocao.isPresent());
        assertEquals(estoqueInicial, pecaAposRemocao.get().getQuantidadeEstoque()); // 10
    }

    @Test
    void deveRestaurarEstoqueQuandoCancelarOrdemServico() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        Peca peca = criarPecaTeste();
        int estoqueInicial = peca.getQuantidadeEstoque(); // 10

        // Adicionar item peça (reduz estoque)
        var commandItem = new AdicionarItemOSCommand(os.getId(), "Filtro de óleo", TipoItemOrdemServico.PECA, new BigDecimal("25.00"), 3, peca.getId());
        ordemServicoService.adicionarItem(commandItem);

        // Verificar que estoque foi reduzido
        Optional<Peca> pecaAposAdicao = pecaRepository.findById(peca.getId());
        assertTrue(pecaAposAdicao.isPresent());
        assertEquals(estoqueInicial - 3, pecaAposAdicao.get().getQuantidadeEstoque()); // 7

        // Cancelar ordem de serviço (restaura estoque)
        OrdemServico osCancelada = ordemServicoService.cancelar(os.getId());

        // Verificar que itens foram removidos
        assertTrue(osCancelada.getItens().isEmpty());

        // Verificar que estoque foi restaurado
        Optional<Peca> pecaAposCancelamento = pecaRepository.findById(peca.getId());
        assertTrue(pecaAposCancelamento.isPresent());
        assertEquals(estoqueInicial, pecaAposCancelamento.get().getQuantidadeEstoque()); // 10
    }

    @Test
    void deveLancarExcecaoQuandoCancelarOrdemServicoFinalizada() {
        Cliente cliente = criarClienteTeste();
        Veiculo veiculo = criarVeiculoTeste(cliente.getId());
        var commandAbrir = new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId());
        OrdemServico os = ordemServicoService.abrir(commandAbrir);

        // Avançar status até FINALIZADA
        ordemServicoService.avancarStatus(os.getId()); // EM_DIAGNOSTICO
        ordemServicoService.avancarStatus(os.getId()); // AGUARDANDO_APROVACAO
        ordemServicoService.avancarStatus(os.getId()); // EM_EXECUCAO
        OrdemServico osFinalizada = ordemServicoService.avancarStatus(os.getId()); // FINALIZADA

        assertEquals(StatusOrdemServico.FINALIZADA, osFinalizada.getStatus());

        // Tentar cancelar deve lançar exceção
        assertThrows(OrdemServicoException.class, () -> {
            ordemServicoService.cancelar(os.getId());
        });
    }
}
