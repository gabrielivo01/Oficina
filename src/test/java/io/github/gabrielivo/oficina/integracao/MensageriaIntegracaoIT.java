package io.github.gabrielivo.oficina.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.gabrielivo.oficina.application.mensageria.FalhaPublicacaoException;
import io.github.gabrielivo.oficina.application.mensageria.MensagemPublicavel;
import io.github.gabrielivo.oficina.application.mensageria.PublicadorMensagens;
import io.github.gabrielivo.oficina.application.ordemServico.AbrirOrdemServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemOutboxRepository;
import io.github.gabrielivo.oficina.domain.mensageria.MensagemProcessadaRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Aceite da etapa 2.4 com PostgreSQL e RabbitMQ reais: gravar no outbox → a mensagem chega à fila e é consumida;
 * mensagem duplicada → processada uma vez; tipo desconhecido → DLQ; mensagem não roteável → falha de publicação.
 */
class MensageriaIntegracaoIT extends IntegracaoTestBase {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    private OrdemServicoService ordemServicoService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private MensagemOutboxRepository outboxRepository;

    @Autowired
    private MensagemProcessadaRepository inboxRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PublicadorMensagens publicador;

    private OrdemServico abrirOs(String cpf, String placa) {
        Cliente cliente = clienteRepository.save(new Cliente(cpf, "Cliente " + cpf, null, "cliente@example.com", null));
        Veiculo veiculo = veiculoRepository.save(new Veiculo(cliente, placa, "Fiat", "Uno", 2020));
        return ordemServicoService.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId()));
    }

    private static String envelope(String messageId, String tipo, String osId) {
        return """
                {"messageId":"%s","type":"%s","sagaId":"%s","osId":"%s","occurredAt":"2026-11-15T20:00:00Z",
                 "payload":{"statusAnterior":"RECEBIDA","statusNovo":"EM_DIAGNOSTICO","origem":"USUARIO",
                            "motivo":"teste","alteradoEm":"2026-11-15T20:00:00Z"}}
                """.formatted(messageId, tipo, osId, osId);
    }

    private void enviarParaEventos(String corpo) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(
                "oficina.eventos",
                "os.status-alterado",
                new Message(corpo.getBytes(StandardCharsets.UTF_8), propriedades));
    }

    @Test
    void mensagemDuplicadaEProcessadaUmaVez() {
        String osDuplicada = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        String osSentinela = UUID.randomUUID().toString();

        enviarParaEventos(envelope(messageId, "StatusOrdemServicoAlterado", osDuplicada));
        enviarParaEventos(envelope(messageId, "StatusOrdemServicoAlterado", osDuplicada));
        // Consumidor único e ordenado: quando a sentinela é processada, as duas cópias já passaram.
        enviarParaEventos(envelope(UUID.randomUUID().toString(), "StatusOrdemServicoAlterado", osSentinela));

        verify(statusNotificationPort, timeout(ESPERA.toMillis()))
                .enviarAtualizacao(eq(osSentinela), eq(StatusOrdemServico.EM_DIAGNOSTICO), anyString());
        verify(statusNotificationPort)
                .enviarAtualizacao(eq(osDuplicada), eq(StatusOrdemServico.EM_DIAGNOSTICO), anyString());
        assertThat(inboxRepository.existsById(messageId)).isTrue();
    }

    @Test
    void mensagemDeTipoSemManipuladorVaiParaDlqSemEntrarNoInbox() {
        String messageId = UUID.randomUUID().toString();
        String osId = UUID.randomUUID().toString();

        enviarParaEventos(envelope(messageId, "TipoDesconhecido", osId));

        Message naDlq = rabbitTemplate.receive("os.saga.eventos.dlq", ESPERA.toMillis());
        assertThat(naDlq).isNotNull();
        assertThat(new String(naDlq.getBody(), StandardCharsets.UTF_8)).contains(messageId);
        assertThat(inboxRepository.existsById(messageId)).isFalse();
        verify(statusNotificationPort, never())
                .enviarAtualizacao(eq(osId), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void publicacaoNaoRoteavelFalhaEmVezDeSerDadaComoEnviada() {
        MensagemPublicavel semDestino = new MensagemPublicavel(
                UUID.randomUUID().toString(), "Teste", "oficina.comandos", "fila.que.nao.existe", "{}", null);

        assertThrows(FalhaPublicacaoException.class, () -> publicador.publicar(semDestino));
    }
}
