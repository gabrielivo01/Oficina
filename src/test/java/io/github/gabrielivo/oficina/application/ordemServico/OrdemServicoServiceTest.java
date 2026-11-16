package io.github.gabrielivo.oficina.application.ordemServico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoException;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrdemServicoServiceTest {

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private OrquestradorSagaService orquestradorSaga;

    @Mock
    private PublicadorEventosOrdemServico publicadorStatus;

    private OrdemServicoService service;

    @BeforeEach
    void setUp() {
        service = new OrdemServicoService(
                ordemServicoRepository,
                clienteRepository,
                veiculoRepository,
                orquestradorSaga,
                publicadorStatus,
                new SimpleMeterRegistry());
    }

    @Test
    void abrirOSIniciaSagaNaOrdemPersistida() {
        Cliente cliente = clienteComEmail();
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        when(clienteRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(veiculoRepository.findById(veiculo.getId())).thenReturn(Optional.of(veiculo));
        when(ordemServicoRepository.save(any(OrdemServico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdemServico os = service.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId()));

        assertEquals(cliente.getId(), os.getCliente().getId());
        assertEquals(veiculo.getId(), os.getVeiculo().getId());
        verify(orquestradorSaga).iniciar(os);
    }

    @Test
    void abrirOSSemEmailDoClienteFalhaAntesDeConsultarVeiculo() {
        Cliente cliente = new Cliente("11122233344", "Cliente", null, null);
        when(clienteRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));

        assertThrows(
                OrdemServicoException.class,
                () -> service.abrir(new AbrirOrdemServicoCommand(cliente.getId(), "veiculo-1")));

        verifyNoInteractions(veiculoRepository, orquestradorSaga);
    }

    @Test
    void cancelarOSDelegaCompensacaoParaSaga() {
        Cliente cliente = clienteComEmail();
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);
        when(ordemServicoRepository.findById(os.getId())).thenReturn(Optional.of(os));
        when(orquestradorSaga.solicitarCancelamento(os.getId(), "Desistência")).thenReturn(os);

        assertEquals(os, service.cancelar(os.getId(), "Desistência"));

        verify(orquestradorSaga).solicitarCancelamento(os.getId(), "Desistência");
        verify(publicadorStatus, never()).salvarEPublicar(any());
    }

    private Cliente clienteComEmail() {
        return new Cliente("11122233344", "Cliente", "11999999999", "cliente@example.com", null);
    }
}
