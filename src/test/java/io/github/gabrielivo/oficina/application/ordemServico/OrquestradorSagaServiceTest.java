package io.github.gabrielivo.oficina.application.ordemServico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.MensageriaProperties;
import io.github.gabrielivo.oficina.application.mensageria.OutboxService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.EtapaSaga;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServico;
import io.github.gabrielivo.oficina.domain.saga.SagaOrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.saga.SituacaoSaga;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OrquestradorSagaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-16T10:00:00Z");

    @Mock
    private SagaOrdemServicoRepository sagaRepository;

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private PublicadorEventosOrdemServico publicadorStatus;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private OrquestradorSagaService service;

    @BeforeEach
    void setUp() {
        service = new OrquestradorSagaService(
                sagaRepository,
                ordemServicoRepository,
                outboxService,
                publicadorStatus,
                new MensageriaProperties(
                        "oficina.eventos",
                        "oficina.comandos",
                        "os.saga.eventos",
                        new MensageriaProperties.Outbox(true, Duration.ofSeconds(1), 50, Duration.ofSeconds(5))),
                new SagaProperties(Duration.ofMinutes(30), Duration.ofSeconds(30), Duration.ofSeconds(1), 3),
                new SimpleMeterRegistry(),
                jsonMapper);
    }

    @Test
    void iniciarPersisteSagaEComandoDeDiagnosticoNaOutbox() {
        OrdemServico os = ordemServico();
        when(sagaRepository.save(any(SagaOrdemServico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.iniciar(os);

        verify(sagaRepository).save(any(SagaOrdemServico.class));
        verify(outboxService)
                .registrar(
                        eq("oficina.comandos"),
                        eq("execution.comandos"),
                        eq("SolicitarDiagnostico"),
                        eq(os.getId()),
                        any(Instant.class),
                        argThat(payload -> payload instanceof Map<?, ?> dados
                                && dados.get("clienteId").equals(os.getCliente().getId())
                                && ((Map<?, ?>) dados.get("veiculo"))
                                        .get("placa")
                                        .equals("ABC1234")));
    }

    @Test
    void processarEventoAtualizaStatusPelaSagaEPersisteEstado() {
        OrdemServico os = ordemServico();
        SagaOrdemServico saga = new SagaOrdemServico(os.getId(), AGORA);
        saga.iniciar(AGORA);
        when(sagaRepository.findById(os.getId())).thenReturn(Optional.of(saga));
        when(ordemServicoRepository.findById(os.getId())).thenReturn(Optional.of(os));
        EnvelopeMensagem evento = new EnvelopeMensagem(
                "message-1",
                "DiagnosticoIniciado",
                os.getId(),
                os.getId(),
                AGORA.plusSeconds(1),
                jsonMapper.valueToTree(Map.of("tarefaId", "tarefa-1")));

        service.processar(evento);

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, os.getStatus());
        assertEquals(1, saga.getHistorico().size());
        verify(publicadorStatus).salvarEPublicar(os);
        verify(sagaRepository).save(saga);
    }

    @Test
    void rejeitaEventoComIdsDeSagaEOrdemDiferentesAntesDeCarregarEstado() {
        EnvelopeMensagem evento = new EnvelopeMensagem(
                "message-1", "DiagnosticoIniciado", "outra-os", "os-1", AGORA, jsonMapper.valueToTree(Map.of()));

        assertThrows(IllegalArgumentException.class, () -> service.processar(evento));

        verifyNoInteractions(sagaRepository, ordemServicoRepository, outboxService, publicadorStatus);
    }

    @Test
    void diagnosticoConcluidoPersisteSnapshotEEnriqueceComandoDeOrcamento() {
        OrdemServico os = ordemServico();
        SagaOrdemServico saga = new SagaOrdemServico(os.getId(), AGORA);
        saga.iniciar(AGORA);
        when(sagaRepository.findById(os.getId())).thenReturn(Optional.of(saga));
        when(ordemServicoRepository.findById(os.getId())).thenReturn(Optional.of(os));
        service.processar(evento(os, "DiagnosticoIniciado", Map.of(), 1));
        EnvelopeMensagem diagnostico = evento(
                os,
                "DiagnosticoConcluido",
                Map.of(
                        "reservaId",
                        "reserva-1",
                        "itens",
                        List.of(Map.of(
                                "tipo",
                                "SERVICO",
                                "descricao",
                                "Revisão",
                                "valorUnitario",
                                new BigDecimal("25.00"),
                                "quantidade",
                                1,
                                "valorTotal",
                                new BigDecimal("25.00"))),
                        "valorTotal",
                        new BigDecimal("25.00")),
                2);

        service.processar(diagnostico);

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, os.getStatus());
        assertEquals(new BigDecimal("25.00"), os.getValorTotal());
        assertEquals("Revisão", os.getItens().getFirst().getDescricao());
        verify(outboxService)
                .registrar(
                        eq("oficina.comandos"),
                        eq("billing.comandos"),
                        eq("GerarOrcamento"),
                        eq(os.getId()),
                        any(Instant.class),
                        argThat(payload -> payload instanceof Map<?, ?> dados
                                && dados.containsKey("itens")
                                && ((Map<?, ?>) dados.get("cliente"))
                                        .get("email")
                                        .equals("cliente@example.com")
                                && ((Map<?, ?>) dados.get("veiculo"))
                                        .get("placa")
                                        .equals("ABC1234")));
    }

    @Test
    void rejeitaSnapshotDeDiagnosticoComTotalDivergente() {
        OrdemServico os = ordemServico();
        SagaOrdemServico saga = new SagaOrdemServico(os.getId(), AGORA);
        saga.iniciar(AGORA);
        when(sagaRepository.findById(os.getId())).thenReturn(Optional.of(saga));
        when(ordemServicoRepository.findById(os.getId())).thenReturn(Optional.of(os));
        service.processar(evento(os, "DiagnosticoIniciado", Map.of(), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.processar(evento(
                        os,
                        "DiagnosticoConcluido",
                        Map.of(
                                "reservaId",
                                "reserva-1",
                                "itens",
                                List.of(Map.of(
                                        "tipo",
                                        "SERVICO",
                                        "descricao",
                                        "Revisão",
                                        "valorUnitario",
                                        new BigDecimal("25.00"),
                                        "quantidade",
                                        1,
                                        "valorTotal",
                                        new BigDecimal("25.00"))),
                                "valorTotal",
                                new BigDecimal("30.00")),
                        2)));
    }

    @Test
    void jobIniciaDiagnosticosPendentesEPublicaComandoNovamente() {
        OrdemServico os = ordemServico();
        SagaOrdemServico saga = new SagaOrdemServico(os.getId(), AGORA);
        when(sagaRepository.findTop100ByDiagnosticoSolicitadoFalseAndSituacaoAndEtapaAtualOrderByCriadoEmAsc(
                        SituacaoSaga.EM_ANDAMENTO, EtapaSaga.AGUARDANDO_DIAGNOSTICO_INICIADO))
                .thenReturn(List.of(saga));
        when(ordemServicoRepository.findById(os.getId())).thenReturn(Optional.of(os));

        service.iniciarDiagnosticosPendentes();

        assertTrue(saga.isDiagnosticoSolicitado());
        verify(outboxService)
                .registrar(
                        eq("oficina.comandos"),
                        eq("execution.comandos"),
                        eq("SolicitarDiagnostico"),
                        eq(os.getId()),
                        any(Instant.class),
                        any(Map.class));
        verify(sagaRepository).save(saga);
    }

    @Test
    void consultaSagaRetornaEstadoPersistido() {
        SagaOrdemServico saga = new SagaOrdemServico("os-1", AGORA);
        when(sagaRepository.findById("os-1")).thenReturn(Optional.of(saga));

        assertEquals(saga, service.consultar("os-1"));
    }

    private EnvelopeMensagem evento(OrdemServico os, String tipo, Map<String, Object> payload, long offsetSeconds) {
        return new EnvelopeMensagem(
                "message-" + offsetSeconds,
                tipo,
                os.getId(),
                os.getId(),
                AGORA.plusSeconds(offsetSeconds),
                jsonMapper.valueToTree(payload));
    }

    private OrdemServico ordemServico() {
        Cliente cliente = new Cliente("11122233344", "Cliente", "11999999999", "cliente@example.com", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        return new OrdemServico(cliente, veiculo);
    }
}
