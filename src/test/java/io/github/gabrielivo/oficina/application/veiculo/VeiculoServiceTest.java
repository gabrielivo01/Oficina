package io.github.gabrielivo.oficina.application.veiculo;

import io.github.gabrielivo.oficina.application.cliente.ClienteService;
import io.github.gabrielivo.oficina.application.cliente.CriarClienteCommand;
import io.github.gabrielivo.oficina.application.cliente.EnderecoCommand;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteException;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoException;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({VeiculoService.class, ClienteService.class})
class VeiculoServiceTest {

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private VeiculoService veiculoService;

    @Autowired
    private ClienteService clienteService;

    private Cliente criarClienteTeste() {
        var endereco = new EnderecoCommand("12345678", "Rua Teste", "123", null, "Centro", "São Paulo", "SP");
        var cpf = "12345678901"; // CPF fixo
        try {
            var command = new CriarClienteCommand(cpf, "Cliente Teste", "(11) 99999-9999", endereco);
            return clienteService.criar(command);
        } catch (Exception e) {
            return clienteRepository.findByCpf(cpf).orElseThrow();
        }
    }

    private Cliente criarClienteUnico() {
        var endereco = new EnderecoCommand("12345678", "Rua Teste", "123", null, "Centro", "São Paulo", "SP");
        var cpf = java.util.UUID.randomUUID().toString().substring(0, 11).replace("-", "");
        var command = new CriarClienteCommand(cpf, "Cliente " + cpf, "(11) 99999-9999", endereco);
        return clienteService.criar(command);
    }

    @Test
    void deveCriarVeiculoQuandoDadosSaoValidos() {
        Cliente cliente = criarClienteTeste();

        var command = new CriarVeiculoCommand(cliente.getId(), "XYZ-9876", "Toyota", "Corolla", 2023);
        Veiculo veiculo = veiculoService.criar(command);

        assertNotNull(veiculo.getId());
        assertEquals(cliente.getId(), veiculo.getCliente().getId());
        assertEquals("XYZ-9876", veiculo.getPlaca());
        assertEquals("Toyota", veiculo.getMarca());
        assertEquals("Corolla", veiculo.getModelo());
        assertEquals(2023, veiculo.getAno());

        Optional<Veiculo> veiculoSalvo = veiculoRepository.findById(veiculo.getId());
        assertTrue(veiculoSalvo.isPresent());
        assertEquals("XYZ-9876", veiculoSalvo.get().getPlaca());
    }

    @Test
    void deveLancarExcecaoQuandoCriarVeiculoComClienteInexistente() {
        String clienteIdInexistente = "cliente-inexistente";

        var command = new CriarVeiculoCommand(clienteIdInexistente, "ABC-1111", "Fiat", "Uno", 2020);

        assertThrows(ClienteException.class, () -> {
            veiculoService.criar(command);
        });
    }

    @Test
    void deveLancarExcecaoQuandoCriarVeiculoComPlacaDuplicada() {
        Cliente cliente = criarClienteTeste();

        var command1 = new CriarVeiculoCommand(cliente.getId(), "DUP-1234", "Fiat", "Uno", 2020);
        veiculoService.criar(command1);

        var command2 = new CriarVeiculoCommand(cliente.getId(), "DUP-1234", "Chevrolet", "Cruze", 2022);

        assertThrows(VeiculoException.class, () -> {
            veiculoService.criar(command2);
        });
    }

    @Test
    void deveBuscarVeiculoPorIdQuandoExiste() {
        Cliente cliente = criarClienteTeste();
        var commandCriar = new CriarVeiculoCommand(cliente.getId(), "BSC-5555", "Volkswagen", "Gol", 2021);
        Veiculo veiculoCriado = veiculoService.criar(commandCriar);

        Veiculo veiculoBuscado = veiculoService.buscarPorId(veiculoCriado.getId());

        assertNotNull(veiculoBuscado);
        assertEquals(veiculoCriado.getId(), veiculoBuscado.getId());
        assertEquals("BSC-5555", veiculoBuscado.getPlaca());
        assertEquals("Volkswagen", veiculoBuscado.getMarca());
    }

    @Test
    void deveLancarExcecaoQuandoBuscarVeiculoPorIdInexistente() {
        String idInexistente = "veiculo-inexistente";

        assertThrows(VeiculoException.class, () -> {
            veiculoService.buscarPorId(idInexistente);
        });
    }

    @Test
    void deveListarTodosOsVeiculos() {
        Cliente cliente = criarClienteTeste();

        var command1 = new CriarVeiculoCommand(cliente.getId(), "VEI-0001", "Fiat", "Uno", 2020);
        veiculoService.criar(command1);

        var command2 = new CriarVeiculoCommand(cliente.getId(), "VEI-0002", "Chevrolet", "Cruze", 2022);
        veiculoService.criar(command2);

        List<Veiculo> veiculos = veiculoService.listarTodos();

        assertNotNull(veiculos);
        assertTrue(veiculos.size() >= 2);
        assertTrue(veiculos.stream().anyMatch(v -> v.getPlaca().equals("VEI-0001")));
        assertTrue(veiculos.stream().anyMatch(v -> v.getPlaca().equals("VEI-0002")));
    }

    @Test
    void deveListarVeiculosPorCliente() {
        Cliente cliente1 = criarClienteUnico();
        Cliente cliente2 = criarClienteUnico();

        var command1 = new CriarVeiculoCommand(cliente1.getId(), "CLI1-001", "Fiat", "Uno", 2020);
        veiculoService.criar(command1);

        var command2 = new CriarVeiculoCommand(cliente1.getId(), "CLI1-002", "Chevrolet", "Cruze", 2022);
        veiculoService.criar(command2);

        var command3 = new CriarVeiculoCommand(cliente2.getId(), "CLI2-001", "Toyota", "Corolla", 2023);
        veiculoService.criar(command3);

        List<Veiculo> veiculosCliente1 = veiculoService.listarPorCliente(cliente1.getId());

        assertNotNull(veiculosCliente1);
        assertEquals(2, veiculosCliente1.size());
        assertTrue(veiculosCliente1.stream().allMatch(v -> v.getCliente().getId().equals(cliente1.getId())));
        assertTrue(veiculosCliente1.stream().anyMatch(v -> v.getPlaca().equals("CLI1-001")));
        assertTrue(veiculosCliente1.stream().anyMatch(v -> v.getPlaca().equals("CLI1-002")));
    }

    @Test
    void deveAtualizarVeiculoQuandoDadosSaoValidos() {
        Cliente cliente = criarClienteTeste();
        var commandCriar = new CriarVeiculoCommand(cliente.getId(), "ATU-0001", "Fiat", "Uno", 2020);
        Veiculo veiculoCriado = veiculoService.criar(commandCriar);

        var commandAtualizar = new AtualizarVeiculoCommand("Chevrolet", "Onix", 2023);
        Veiculo veiculoAtualizado = veiculoService.atualizar(veiculoCriado.getId(), commandAtualizar);

        assertEquals(veiculoCriado.getId(), veiculoAtualizado.getId());
        assertEquals("ATU-0001", veiculoAtualizado.getPlaca()); // Placa não muda
        assertEquals("Chevrolet", veiculoAtualizado.getMarca());
        assertEquals("Onix", veiculoAtualizado.getModelo());
        assertEquals(2023, veiculoAtualizado.getAno());

        // Verificar que foi salvo no banco
        Optional<Veiculo> veiculoSalvo = veiculoRepository.findById(veiculoCriado.getId());
        assertTrue(veiculoSalvo.isPresent());
        assertEquals("Chevrolet", veiculoSalvo.get().getMarca());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarVeiculoInexistente() {
        String idInexistente = "veiculo-inexistente";
        var commandAtualizar = new AtualizarVeiculoCommand("Fiat", "Uno", 2025);

        assertThrows(VeiculoException.class, () -> {
            veiculoService.atualizar(idInexistente, commandAtualizar);
        });
    }

    @Test
    void deveDeletarVeiculoQuandoVeiculoExiste() {
        Cliente cliente = criarClienteTeste();
        var commandCriar = new CriarVeiculoCommand(cliente.getId(), "DEL-0001", "Fiat", "Uno", 2020);
        Veiculo veiculoCriado = veiculoService.criar(commandCriar);

        // Verificar que existe
        Optional<Veiculo> veiculoAntes = veiculoRepository.findById(veiculoCriado.getId());
        assertTrue(veiculoAntes.isPresent());

        // Deletar
        veiculoService.deletar(veiculoCriado.getId());

        // Verificar que não existe mais
        Optional<Veiculo> veiculoDepois = veiculoRepository.findById(veiculoCriado.getId());
        assertFalse(veiculoDepois.isPresent());
    }

    @Test
    void deveLancarExcecaoQuandoDeletarVeiculoInexistente() {
        String idInexistente = "veiculo-inexistente";

        assertThrows(VeiculoException.class, () -> {
            veiculoService.deletar(idInexistente);
        });
    }
}
