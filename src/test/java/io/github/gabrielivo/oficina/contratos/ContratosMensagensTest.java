package io.github.gabrielivo.oficina.contratos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import io.github.gabrielivo.oficina.application.mensageria.OutboxService;
import io.github.gabrielivo.oficina.application.ordemServico.NotificacaoStatusOrdemServicoManipulador;
import io.github.gabrielivo.oficina.application.ordemServico.StatusNotificationPort;
import io.github.gabrielivo.oficina.application.ordemServico.StatusOrdemServicoAlteradoPayload;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutbox;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutboxRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testes de contrato do catálogo {@code documentacao/contratos_mensagens.md} (regra 6 do plano da Fase 4): os exemplos
 * em {@code src/test/resources/contratos} cobrem todos os comandos e eventos da seção 3.2, são iguais aos do catálogo
 * e respeitam o envelope; o evento já produzido e consumido pelo OS Service é verificado contra o código real.
 */
class ContratosMensagensTest {

    /** Seção 3.2 do plano, incluindo confirmações de compensação, e StatusOrdemServicoAlterado. */
    private static final Set<String> TIPOS = Set.of(
            "SolicitarDiagnostico",
            "GerarOrcamento",
            "CriarCobranca",
            "IniciarExecucao",
            "CancelarExecucao",
            "LiberarReservaPecas",
            "CancelarCobranca",
            "EstornarPagamento",
            "DiagnosticoIniciado",
            "DiagnosticoConcluido",
            "OrcamentoGerado",
            "OrcamentoAprovado",
            "OrcamentoRecusado",
            "CobrancaCriada",
            "CobrancaCancelada",
            "PagamentoConfirmado",
            "PagamentoRejeitado",
            "ExecucaoIniciada",
            "ExecucaoRecusada",
            "ExecucaoCancelada",
            "ExecucaoFinalizada",
            "ReservaPecasLiberada",
            "PagamentoEstornado",
            "StatusOrdemServicoAlterado");

    private static final Path CATALOGO = Path.of("documentacao/contratos_mensagens.md");
    private static final Pattern SECAO_COM_EXEMPLO =
            Pattern.compile("^### `(\\w+)`$.*?^```json$(.*?)^```$", Pattern.MULTILINE | Pattern.DOTALL);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    static Stream<String> tipos() {
        return TIPOS.stream().sorted();
    }

    private String exemplo(String tipo) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contratos/" + tipo + ".json")) {
            assertThat(in).as("contratos/%s.json", tipo).isNotNull();
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void deveHaverUmExemploPorTipoESemSobras() throws IOException, URISyntaxException {
        Path pasta = Path.of(getClass().getResource("/contratos").toURI());
        Set<String> arquivos = new TreeSet<>();
        try (Stream<Path> lista = Files.list(pasta)) {
            lista.map(p -> p.getFileName().toString()).forEach(arquivos::add);
        }

        assertThat(arquivos)
                .containsExactlyInAnyOrderElementsOf(
                        TIPOS.stream().map(t -> t + ".json").toList());
    }

    @ParameterizedTest
    @MethodSource("tipos")
    void exemploDeveRespeitarOEnvelopePadrao(String tipo) throws IOException {
        EnvelopeMensagem envelope = jsonMapper.readValue(exemplo(tipo), EnvelopeMensagem.class);

        assertThat(envelope.type()).isEqualTo(tipo);
        assertThat(UUID.fromString(envelope.messageId())).isNotNull();
        assertThat(UUID.fromString(envelope.osId())).isNotNull();
        assertThat(envelope.sagaId()).as("sagaId = id da OS").isEqualTo(envelope.osId());
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.payload()).isNotNull();
        assertThat(envelope.payload().isObject()).isTrue();
    }

    @Test
    void messageIdsDosExemplosDevemSerUnicos() throws IOException {
        Set<String> ids = new HashSet<>();
        for (String tipo : TIPOS) {
            ids.add(jsonMapper.readTree(exemplo(tipo)).get("messageId").asString());
        }
        assertThat(ids).hasSize(TIPOS.size());
    }

    @Test
    void catalogoDeveDocumentarTodosOsTiposComOMesmoExemploDosArquivos() throws IOException {
        Map<String, JsonNode> doCatalogo = new HashMap<>();
        Matcher secao = SECAO_COM_EXEMPLO.matcher(Files.readString(CATALOGO));
        while (secao.find()) {
            doCatalogo.put(secao.group(1), jsonMapper.readTree(secao.group(2)));
        }

        assertThat(doCatalogo.keySet()).containsExactlyInAnyOrderElementsOf(TIPOS);
        for (String tipo : TIPOS) {
            assertThat(doCatalogo.get(tipo))
                    .as("exemplo de %s no catálogo x contratos/%s.json", tipo, tipo)
                    .isEqualTo(jsonMapper.readTree(exemplo(tipo)));
        }
    }

    // ---------------------------------------------------------------- StatusOrdemServicoAlterado (Fase 2)

    @Test
    void consumidorDeStatusOrdemServicoAlteradoDeveEntenderOExemplo() throws IOException {
        EnvelopeMensagem envelope = jsonMapper.readValue(exemplo("StatusOrdemServicoAlterado"), EnvelopeMensagem.class);
        StatusOrdemServicoAlteradoPayload payload =
                jsonMapper.treeToValue(envelope.payload(), StatusOrdemServicoAlteradoPayload.class);
        StatusNotificationPort port = mock(StatusNotificationPort.class);

        new NotificacaoStatusOrdemServicoManipulador(port, jsonMapper).manipular(envelope);

        assertThat(payload.statusAnterior()).isEqualTo(StatusOrdemServico.RECEBIDA);
        assertThat(payload.statusNovo()).isEqualTo(StatusOrdemServico.EM_DIAGNOSTICO);
        assertThat(payload.origem()).isEqualTo(OrigemMudancaStatus.SAGA);
        assertThat(payload.alteradoEm()).isEqualTo(Instant.parse("2026-11-15T13:10:00.123Z"));
        verify(port)
                .enviarAtualizacao(
                        "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
                        StatusOrdemServico.EM_DIAGNOSTICO,
                        "Status alterado de RECEBIDA para EM_DIAGNOSTICO. Motivo: DiagnosticoIniciado");
    }

    @Test
    void produtorDeStatusOrdemServicoAlteradoDeveGerarOMesmoFormatoDoExemplo() throws IOException {
        MensagemOutboxRepository repository = mock(MensagemOutboxRepository.class);
        OutboxService outbox = new OutboxService(repository, jsonMapper, () -> null);
        Instant quando = Instant.parse("2026-11-15T13:10:00.123Z");

        outbox.registrar(
                "oficina.eventos",
                StatusOrdemServicoAlteradoPayload.ROUTING_KEY,
                StatusOrdemServicoAlteradoPayload.TIPO,
                "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
                quando,
                new StatusOrdemServicoAlteradoPayload(
                        StatusOrdemServico.RECEBIDA,
                        StatusOrdemServico.EM_DIAGNOSTICO,
                        OrigemMudancaStatus.SAGA,
                        "DiagnosticoIniciado",
                        quando));

        ArgumentCaptor<MensagemOutbox> gravada = ArgumentCaptor.forClass(MensagemOutbox.class);
        verify(repository).save(gravada.capture());
        JsonNode produzido = jsonMapper.readTree(gravada.getValue().getPayload());
        JsonNode esperado = jsonMapper.readTree(exemplo("StatusOrdemServicoAlterado"));
        assertThat(campos(produzido)).isEqualTo(campos(esperado));
        assertThat(produzido.get("type")).isEqualTo(esperado.get("type"));
        assertThat(produzido.get("payload")).isEqualTo(esperado.get("payload"));
        assertThat(Instant.parse(produzido.get("occurredAt").asString())).isEqualTo(quando);
        verify(repository).save(any());
    }

    /** Caminhos de todos os campos (ex.: {@code payload.statusNovo}), para comparar formato sem comparar valores. */
    private static Set<String> campos(JsonNode no) {
        Set<String> caminhos = new TreeSet<>();
        coletar(no, "", caminhos);
        return caminhos;
    }

    private static void coletar(JsonNode no, String prefixo, Set<String> caminhos) {
        for (Map.Entry<String, JsonNode> campo : no.properties()) {
            String caminho = prefixo.isEmpty() ? campo.getKey() : prefixo + "." + campo.getKey();
            caminhos.add(caminho);
            if (campo.getValue().isObject()) {
                coletar(campo.getValue(), caminho, caminhos);
            }
        }
    }
}
