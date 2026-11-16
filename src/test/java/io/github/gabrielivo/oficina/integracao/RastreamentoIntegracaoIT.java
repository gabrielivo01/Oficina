package io.github.gabrielivo.oficina.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.gabrielivo.oficina.application.mensageria.ContextoRastreamento;
import io.github.gabrielivo.oficina.application.ordemServico.AbrirOrdemServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutbox;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutboxRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import io.github.gabrielivo.oficina.infrastructure.security.JwtService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aceite da etapa 2.5 (parte automatizada): a requisição HTTP que muda o status, a publicação feita depois pelo outbox
 * e o consumo da mensagem ficam no mesmo trace (traceparent salvo no outbox e propagado nos headers AMQP). A
 * exportação para o Jaeger é validada manualmente com a plataforma local (ver README).
 */
class RastreamentoIntegracaoIT extends IntegracaoTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Tracer tracer;

    @Autowired
    private OrdemServicoService ordemServicoService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private MensagemOutboxRepository outboxRepository;

    @Autowired
    private ContextoRastreamento contextoRastreamento;

    @Autowired
    private OrdemServicoRepository ordemServicoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void contextoDeRastreamentoDevolveOTraceparentW3cDoSpanCorrente() {
        Span span = tracer.nextSpan().name("teste").start();
        try (Tracer.SpanInScope escopo = tracer.withSpan(span)) {
            assertThat(contextoRastreamento.traceparentAtual())
                    .isEqualTo("00-" + span.context().traceId() + "-"
                            + span.context().spanId() + "-01");
        } finally {
            span.end();
        }
    }

    @Test
    void requisicaoHttpPublicacaoEConsumoFicamNoMesmoTrace() throws Exception {
        Cliente cliente =
                clienteRepository.save(new Cliente("82176548072", "Cliente Trace", null, "trace@example.com", null));
        Veiculo veiculo = veiculoRepository.save(new Veiculo(cliente, "TRC1A23", "Fiat", "Uno", 2020));
        OrdemServico os = ordemServicoService.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId()));
        // Leva a OS até FINALIZADA como a Saga faria; a única ação HTTP medida é a entrega
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            OrdemServico emAndamento =
                    ordemServicoRepository.findById(os.getId()).orElseThrow();
            for (StatusOrdemServico proximo : List.of(
                    StatusOrdemServico.EM_DIAGNOSTICO,
                    StatusOrdemServico.AGUARDANDO_APROVACAO,
                    StatusOrdemServico.AGUARDANDO_PAGAMENTO,
                    StatusOrdemServico.EM_EXECUCAO,
                    StatusOrdemServico.FINALIZADA)) {
                emAndamento.alterarStatus(proximo, OrigemMudancaStatus.SAGA, "preparação do teste");
            }
            emAndamento.extrairEventos();
            ordemServicoRepository.save(emAndamento);
        });
        AtomicReference<String> traceNoConsumo = new AtomicReference<>();
        doAnswer(invocacao -> {
                    Span atual = tracer.currentSpan();
                    traceNoConsumo.set(
                            atual == null ? "sem-span" : atual.context().traceId());
                    return null;
                })
                .when(statusNotificationPort)
                .enviarAtualizacao(eq(os.getId()), any(), anyString());

        String traceDaRequisicao = mockMvc.perform(post("/ordens-servico/{id}/entregar", os.getId())
                        .header("Authorization", "Bearer " + jwtService.gerarToken("admin")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Correlation-Id");

        assertThat(traceDaRequisicao).hasSize(32);
        MensagemOutbox gravada = outboxRepository.findAll().stream()
                .filter(m -> "StatusOrdemServicoAlterado".equals(m.getTipo())
                        && m.getPayload().contains(os.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(gravada.getTraceParent()).startsWith("00-" + traceDaRequisicao + "-");
        await().atMost(Duration.ofSeconds(20)).until(() -> traceNoConsumo.get() != null);
        assertThat(traceNoConsumo.get()).isEqualTo(traceDaRequisicao);
    }
}
