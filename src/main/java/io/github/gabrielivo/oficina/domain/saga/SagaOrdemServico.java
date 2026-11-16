package io.github.gabrielivo.oficina.domain.saga;

import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "saga_ordem_servico")
public class SagaOrdemServico {

    @Id
    @Column(name = "os_id", length = 255)
    private String osId;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_atual", nullable = false, length = 50)
    private EtapaSaga etapaAtual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SituacaoSaga situacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_compensacao", length = 40)
    private MotivoCompensacao motivoCompensacao;

    @Column(name = "reserva_id", length = 36)
    private String reservaId;

    @Column(name = "diagnostico_solicitado", nullable = false)
    private boolean diagnosticoSolicitado;

    @Column(name = "orcamento_id", length = 36)
    private String orcamentoId;

    @Column(name = "valor_orcamento", precision = 10, scale = 2)
    private BigDecimal valorOrcamento;

    @Column(name = "cobranca_id", length = 36)
    private String cobrancaId;

    @Column(name = "pagamento_id_externo", length = 200)
    private String pagamentoIdExterno;

    @Column(name = "reserva_liberada", nullable = false)
    private boolean reservaLiberada;

    @Column(name = "prazo_expiracao")
    private Instant prazoExpiracao;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "falha_compensacao", length = 1000)
    private String falhaCompensacao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    @ElementCollection
    @CollectionTable(name = "historico_eventos_saga", joinColumns = @JoinColumn(name = "saga_id"))
    @OrderColumn(name = "sequencia")
    private List<EventoSagaRegistrado> historico = new ArrayList<>();

    protected SagaOrdemServico() {}

    public SagaOrdemServico(String osId, Instant criadaEm) {
        if (osId == null || osId.isBlank() || criadaEm == null) {
            throw new IllegalArgumentException("osId e instante de criação são obrigatórios.");
        }
        this.osId = osId;
        this.etapaAtual = EtapaSaga.AGUARDANDO_DIAGNOSTICO_INICIADO;
        this.situacao = SituacaoSaga.EM_ANDAMENTO;
        this.criadoEm = criadaEm;
        this.atualizadoEm = criadaEm;
    }

    public DecisaoSaga iniciar(Instant agora) {
        if (diagnosticoSolicitado
                || etapaAtual != EtapaSaga.AGUARDANDO_DIAGNOSTICO_INICIADO
                || situacao != SituacaoSaga.EM_ANDAMENTO) {
            return DecisaoSaga.ignorada();
        }
        diagnosticoSolicitado = true;
        atualizadoEm = agora;
        return new DecisaoSaga(true, null, List.of(new ComandoSaga("SolicitarDiagnostico", Map.of())));
    }

    public DecisaoSaga aoReceber(EventoSaga evento, Instant agora, Duration prazoPagamento, Duration prazoCompensacao) {
        if (evento == null || agora == null || prazoPagamento == null || prazoCompensacao == null) {
            throw new IllegalArgumentException("Evento e parâmetros temporais da Saga são obrigatórios.");
        }
        if (evento.messageId() != null
                && historico.stream().anyMatch(registro -> evento.messageId().equals(registro.getMessageId()))) {
            return DecisaoSaga.ignorada();
        }

        DecisaoSaga decisao = switch (evento.tipo()) {
            case "DiagnosticoIniciado" -> diagnosticoIniciado(evento);
            case "DiagnosticoConcluido" -> diagnosticoConcluido(evento, agora, prazoCompensacao);
            case "OrcamentoGerado" -> orcamentoGerado(evento);
            case "OrcamentoAprovado" -> orcamentoAprovado(evento, agora, prazoPagamento);
            case "OrcamentoRecusado" -> orcamentoRecusado(evento, agora, prazoCompensacao);
            case "CobrancaCriada" -> cobrancaCriada(evento, agora);
            case "CobrancaCancelada" -> cobrancaCancelada(evento, agora, prazoCompensacao);
            case "PagamentoConfirmado" -> pagamentoConfirmado(evento, agora, prazoCompensacao);
            case "PagamentoRejeitado" -> pagamentoRejeitado(evento, agora, prazoCompensacao);
            case "ExecucaoIniciada" -> execucaoIniciada(evento);
            case "ExecucaoRecusada" -> execucaoRecusada(evento, agora, prazoCompensacao);
            case "ExecucaoCancelada" -> execucaoCancelada(evento, agora, prazoCompensacao);
            case "ExecucaoFinalizada" -> execucaoFinalizada(evento);
            case "PagamentoEstornado" -> pagamentoEstornado(evento, agora, prazoCompensacao);
            case "ReservaPecasLiberada" -> reservaPecasLiberada(evento, agora);
            default -> DecisaoSaga.ignorada();
        };
        registrar(evento, decisao.aplicada());
        atualizadoEm = agora;
        return decisao;
    }

    public DecisaoSaga solicitarCancelamento(String motivo, Instant agora, Duration prazoCompensacao) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO || agora == null || prazoCompensacao == null) {
            return DecisaoSaga.ignorada();
        }
        String detalhe = motivo == null || motivo.isBlank() ? "Cancelamento solicitado" : motivo.strip();
        registrar(new EventoSaga(null, "CancelamentoSolicitado", agora, Map.of("motivo", detalhe)), true);
        motivoCompensacao = MotivoCompensacao.CANCELAMENTO;
        falhaCompensacao = null;
        tentativas = 1;
        prazoExpiracao = agora.plus(prazoCompensacao);
        situacao = SituacaoSaga.COMPENSANDO;

        if (etapaAtual == EtapaSaga.AGUARDANDO_COBRANCA) {
            etapaAtual = EtapaSaga.COMPENSANDO_COBRANCA;
            atualizadoEm = agora;
            return new DecisaoSaga(true, null, List.of());
        }
        if (etapaAtual == EtapaSaga.AGUARDANDO_PAGAMENTO) {
            etapaAtual = EtapaSaga.COMPENSANDO_COBRANCA;
            atualizadoEm = agora;
            return new DecisaoSaga(true, null, List.of(comandoCancelarCobranca()));
        }
        if (etapaAtual == EtapaSaga.AGUARDANDO_INICIO_EXECUCAO
                || etapaAtual == EtapaSaga.AGUARDANDO_FINALIZACAO_EXECUCAO) {
            etapaAtual = EtapaSaga.COMPENSANDO_EXECUCAO;
            atualizadoEm = agora;
            return new DecisaoSaga(true, null, List.of(comandoCancelarExecucao()));
        }

        return iniciarCompensacaoReserva(agora, prazoCompensacao, detalhe);
    }

    public DecisaoSaga expirarPagamento(Instant agora, Duration prazoCompensacao) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO
                || etapaAtual != EtapaSaga.AGUARDANDO_PAGAMENTO
                || prazoExpiracao == null
                || prazoExpiracao.isAfter(agora)) {
            return DecisaoSaga.ignorada();
        }
        registrar(new EventoSaga(null, "PrazoPagamentoExpirado", agora, Map.of()), true);
        motivoCompensacao = MotivoCompensacao.PRAZO_PAGAMENTO_EXPIRADO;
        situacao = SituacaoSaga.COMPENSANDO;
        etapaAtual = EtapaSaga.COMPENSANDO_COBRANCA;
        tentativas = 1;
        falhaCompensacao = null;
        prazoExpiracao = agora.plus(prazoCompensacao);
        atualizadoEm = agora;
        return new DecisaoSaga(true, null, List.of(comandoCancelarCobranca()));
    }

    public DecisaoSaga repetirCompensacao(Instant agora, Duration prazoCompensacao, int maxTentativas) {
        if (situacao != SituacaoSaga.COMPENSANDO || prazoExpiracao == null || prazoExpiracao.isAfter(agora)) {
            return DecisaoSaga.ignorada();
        }
        if (maxTentativas < 1) {
            throw new IllegalArgumentException("O limite de tentativas deve ser positivo.");
        }
        if (tentativas >= maxTentativas) {
            situacao = SituacaoSaga.FALHA_COMPENSACAO;
            falhaCompensacao = "Compensação não confirmada após " + tentativas + " tentativas.";
            prazoExpiracao = null;
            atualizadoEm = agora;
            registrar(new EventoSaga(null, "FalhaCompensacao", agora, Map.of("motivo", falhaCompensacao)), true);
            return new DecisaoSaga(true, null, List.of());
        }
        tentativas++;
        prazoExpiracao = agora.plus(prazoCompensacao);
        atualizadoEm = agora;
        registrar(
                new EventoSaga(null, "TentativaCompensacao", agora, Map.of("tentativa", Integer.toString(tentativas))),
                true);
        return new DecisaoSaga(true, null, comandosCompensacao());
    }

    private DecisaoSaga diagnosticoIniciado(EventoSaga evento) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_DIAGNOSTICO_INICIADO) {
            return DecisaoSaga.ignorada();
        }
        etapaAtual = EtapaSaga.AGUARDANDO_DIAGNOSTICO_CONCLUIDO;
        return new DecisaoSaga(true, StatusOrdemServico.EM_DIAGNOSTICO, List.of());
    }

    private DecisaoSaga diagnosticoConcluido(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if ((situacao == SituacaoSaga.COMPENSANDO
                        || situacao == SituacaoSaga.COMPENSADA
                        || situacao == SituacaoSaga.FALHA_COMPENSACAO)
                && motivoCompensacao == MotivoCompensacao.CANCELAMENTO
                && (etapaAtual == EtapaSaga.COMPENSANDO_RESERVA
                        || etapaAtual == EtapaSaga.COMPENSANDO_COBRANCA
                        || etapaAtual == EtapaSaga.COMPENSANDO_EXECUCAO
                        || etapaAtual == EtapaSaga.COMPENSANDO_ESTORNO
                        || situacao == SituacaoSaga.COMPENSADA
                        || situacao == SituacaoSaga.FALHA_COMPENSACAO)) {
            reservaId = texto(evento.payload(), "reservaId");
            if (etapaAtual == EtapaSaga.COMPENSANDO_COBRANCA
                    || etapaAtual == EtapaSaga.COMPENSANDO_EXECUCAO
                    || etapaAtual == EtapaSaga.COMPENSANDO_ESTORNO) {
                return new DecisaoSaga(true, null, List.of());
            }
            etapaAtual = EtapaSaga.COMPENSANDO_RESERVA;
            situacao = SituacaoSaga.COMPENSANDO;
            tentativas = Math.max(tentativas, 1);
            prazoExpiracao = agora.plus(prazoCompensacao);
            return new DecisaoSaga(true, null, List.of(comandoLiberarReserva()));
        }
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_DIAGNOSTICO_CONCLUIDO) {
            return DecisaoSaga.ignorada();
        }
        reservaId = texto(evento.payload(), "reservaId");
        etapaAtual = EtapaSaga.AGUARDANDO_ORCAMENTO;
        return new DecisaoSaga(true, null, List.of(new ComandoSaga("GerarOrcamento", evento.payload())));
    }

    private DecisaoSaga orcamentoGerado(EventoSaga evento) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_ORCAMENTO) {
            return DecisaoSaga.ignorada();
        }
        orcamentoId = texto(evento.payload(), "orcamentoId");
        valorOrcamento = decimal(evento.payload(), "valorTotal");
        etapaAtual = EtapaSaga.AGUARDANDO_RESPOSTA_ORCAMENTO;
        return new DecisaoSaga(true, StatusOrdemServico.AGUARDANDO_APROVACAO, List.of());
    }

    private DecisaoSaga orcamentoAprovado(EventoSaga evento, Instant agora, Duration prazoPagamento) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_RESPOSTA_ORCAMENTO) {
            return DecisaoSaga.ignorada();
        }
        orcamentoId = texto(evento.payload(), "orcamentoId");
        valorOrcamento = decimal(evento.payload(), "valorTotal");
        etapaAtual = EtapaSaga.AGUARDANDO_COBRANCA;
        prazoExpiracao = agora.plus(prazoPagamento);
        return new DecisaoSaga(
                true,
                null,
                List.of(new ComandoSaga(
                        "CriarCobranca",
                        Map.of(
                                "orcamentoId",
                                orcamentoId,
                                "valor",
                                valorOrcamento,
                                "descricao",
                                "Ordem de serviço " + osId,
                                "prazoPagamento",
                                prazoExpiracao.toString()))));
    }

    private DecisaoSaga orcamentoRecusado(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_RESPOSTA_ORCAMENTO) {
            return DecisaoSaga.ignorada();
        }
        motivoCompensacao = MotivoCompensacao.ORCAMENTO_RECUSADO;
        return iniciarCompensacaoReserva(agora, prazoCompensacao, detalhe(evento.payload()));
    }

    private DecisaoSaga cobrancaCriada(EventoSaga evento, Instant agora) {
        if ((situacao == SituacaoSaga.COMPENSANDO
                        || situacao == SituacaoSaga.COMPENSADA
                        || situacao == SituacaoSaga.FALHA_COMPENSACAO)
                && motivoCompensacao == MotivoCompensacao.CANCELAMENTO
                && (etapaAtual == EtapaSaga.COMPENSANDO_COBRANCA || etapaAtual == EtapaSaga.COMPENSANDO_RESERVA)) {
            cobrancaId = texto(evento.payload(), "cobrancaId");
            situacao = SituacaoSaga.COMPENSANDO;
            etapaAtual = EtapaSaga.COMPENSANDO_COBRANCA;
            tentativas = Math.max(tentativas, 1);
            falhaCompensacao = null;
            prazoExpiracao = agora.plusSeconds(30);
            return new DecisaoSaga(true, null, List.of(comandoCancelarCobranca()));
        }
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_COBRANCA) {
            return DecisaoSaga.ignorada();
        }
        cobrancaId = texto(evento.payload(), "cobrancaId");
        etapaAtual = EtapaSaga.AGUARDANDO_PAGAMENTO;
        prazoExpiracao = instante(texto(evento.payload(), "expiraEm"));
        return new DecisaoSaga(true, StatusOrdemServico.AGUARDANDO_PAGAMENTO, List.of());
    }

    private DecisaoSaga cobrancaCancelada(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if ((situacao != SituacaoSaga.COMPENSANDO && situacao != SituacaoSaga.FALHA_COMPENSACAO)
                || etapaAtual != EtapaSaga.COMPENSANDO_COBRANCA) {
            return DecisaoSaga.ignorada();
        }
        if (cobrancaId != null && !cobrancaId.equals(texto(evento.payload(), "cobrancaId"))) {
            return DecisaoSaga.ignorada();
        }
        if (reservaLiberada) {
            situacao = SituacaoSaga.COMPENSADA;
            prazoExpiracao = null;
            falhaCompensacao = null;
            return new DecisaoSaga(true, StatusOrdemServico.CANCELADA, List.of());
        }
        return iniciarCompensacaoReserva(agora, prazoCompensacao, detalhe(evento.payload()));
    }

    private DecisaoSaga pagamentoConfirmado(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if ((situacao == SituacaoSaga.COMPENSANDO
                        || situacao == SituacaoSaga.COMPENSADA
                        || situacao == SituacaoSaga.FALHA_COMPENSACAO)
                && (etapaAtual == EtapaSaga.COMPENSANDO_COBRANCA || etapaAtual == EtapaSaga.COMPENSANDO_RESERVA)
                && motivoCompensacao != MotivoCompensacao.EXECUCAO_RECUSADA) {
            pagamentoIdExterno = texto(evento.payload(), "pagamentoIdExterno");
            cobrancaId = texto(evento.payload(), "cobrancaId");
            situacao = SituacaoSaga.COMPENSANDO;
            etapaAtual = EtapaSaga.COMPENSANDO_ESTORNO;
            falhaCompensacao = null;
            prazoExpiracao = agora.plus(prazoCompensacao);
            return new DecisaoSaga(true, null, List.of(comandoEstornarPagamento()));
        }
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_PAGAMENTO) {
            return DecisaoSaga.ignorada();
        }
        pagamentoIdExterno = texto(evento.payload(), "pagamentoIdExterno");
        cobrancaId = texto(evento.payload(), "cobrancaId");
        etapaAtual = EtapaSaga.AGUARDANDO_INICIO_EXECUCAO;
        prazoExpiracao = null;
        return new DecisaoSaga(
                true,
                null,
                List.of(new ComandoSaga(
                        "IniciarExecucao", Map.of("reservaId", valorOuVazio(reservaId), "cobrancaId", cobrancaId))));
    }

    private DecisaoSaga pagamentoRejeitado(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_PAGAMENTO) {
            return DecisaoSaga.ignorada();
        }
        motivoCompensacao = MotivoCompensacao.PAGAMENTO_REJEITADO;
        return iniciarCompensacaoReserva(agora, prazoCompensacao, detalhe(evento.payload()));
    }

    private DecisaoSaga execucaoIniciada(EventoSaga evento) {
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_INICIO_EXECUCAO) {
            return DecisaoSaga.ignorada();
        }
        etapaAtual = EtapaSaga.AGUARDANDO_FINALIZACAO_EXECUCAO;
        return new DecisaoSaga(true, StatusOrdemServico.EM_EXECUCAO, List.of());
    }

    private DecisaoSaga execucaoRecusada(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if ((situacao == SituacaoSaga.COMPENSANDO || situacao == SituacaoSaga.FALHA_COMPENSACAO)
                && etapaAtual == EtapaSaga.COMPENSANDO_EXECUCAO) {
            situacao = SituacaoSaga.COMPENSANDO;
            motivoCompensacao = MotivoCompensacao.CANCELAMENTO;
            etapaAtual = EtapaSaga.COMPENSANDO_ESTORNO;
            reservaId = textoOpcional(evento.payload(), "reservaId");
            tentativas = Math.max(tentativas, 1);
            falhaCompensacao = null;
            prazoExpiracao = agora.plus(prazoCompensacao);
            return new DecisaoSaga(true, null, List.of(comandoEstornarPagamento()));
        }
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_INICIO_EXECUCAO) {
            return DecisaoSaga.ignorada();
        }
        motivoCompensacao = MotivoCompensacao.EXECUCAO_RECUSADA;
        etapaAtual = EtapaSaga.COMPENSANDO_ESTORNO;
        situacao = SituacaoSaga.COMPENSANDO;
        tentativas = 1;
        falhaCompensacao = null;
        prazoExpiracao = agora.plus(prazoCompensacao);
        reservaId = textoOpcional(evento.payload(), "reservaId");
        return new DecisaoSaga(true, null, List.of(comandoEstornarPagamento()));
    }

    private DecisaoSaga execucaoFinalizada(EventoSaga evento) {
        if ((situacao == SituacaoSaga.COMPENSANDO || situacao == SituacaoSaga.FALHA_COMPENSACAO)
                && etapaAtual == EtapaSaga.COMPENSANDO_EXECUCAO) {
            situacao = SituacaoSaga.CONCLUIDA;
            prazoExpiracao = null;
            falhaCompensacao = null;
            return new DecisaoSaga(true, StatusOrdemServico.FINALIZADA, List.of());
        }
        if (situacao != SituacaoSaga.EM_ANDAMENTO || etapaAtual != EtapaSaga.AGUARDANDO_FINALIZACAO_EXECUCAO) {
            return DecisaoSaga.ignorada();
        }
        situacao = SituacaoSaga.CONCLUIDA;
        prazoExpiracao = null;
        return new DecisaoSaga(true, StatusOrdemServico.FINALIZADA, List.of());
    }

    private DecisaoSaga execucaoCancelada(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if ((situacao != SituacaoSaga.COMPENSANDO && situacao != SituacaoSaga.FALHA_COMPENSACAO)
                || etapaAtual != EtapaSaga.COMPENSANDO_EXECUCAO) {
            return DecisaoSaga.ignorada();
        }
        situacao = SituacaoSaga.COMPENSANDO;
        String canceladaReservaId = texto(evento.payload(), "reservaId");
        if (reservaId != null && !reservaId.equals(canceladaReservaId)) {
            return DecisaoSaga.ignorada();
        }
        situacao = SituacaoSaga.COMPENSANDO;
        etapaAtual = EtapaSaga.COMPENSANDO_ESTORNO;
        falhaCompensacao = null;
        prazoExpiracao = agora.plus(prazoCompensacao);
        return new DecisaoSaga(true, null, List.of(comandoEstornarPagamento()));
    }

    private DecisaoSaga pagamentoEstornado(EventoSaga evento, Instant agora, Duration prazoCompensacao) {
        if ((situacao != SituacaoSaga.COMPENSANDO && situacao != SituacaoSaga.FALHA_COMPENSACAO)
                || etapaAtual != EtapaSaga.COMPENSANDO_ESTORNO) {
            return DecisaoSaga.ignorada();
        }
        if (cobrancaId != null && !cobrancaId.equals(texto(evento.payload(), "cobrancaId"))) {
            return DecisaoSaga.ignorada();
        }
        if (reservaLiberada) {
            situacao = SituacaoSaga.COMPENSADA;
            prazoExpiracao = null;
            falhaCompensacao = null;
            return new DecisaoSaga(true, StatusOrdemServico.CANCELADA, List.of());
        }
        return iniciarCompensacaoReserva(agora, prazoCompensacao, detalhe(evento.payload()));
    }

    private DecisaoSaga reservaPecasLiberada(EventoSaga evento, Instant agora) {
        if ((situacao != SituacaoSaga.COMPENSANDO && situacao != SituacaoSaga.FALHA_COMPENSACAO)
                || etapaAtual != EtapaSaga.COMPENSANDO_RESERVA) {
            return DecisaoSaga.ignorada();
        }
        String reservaLiberadaId = textoOpcional(evento.payload(), "reservaId");
        if (reservaId != null && !reservaId.equals(reservaLiberadaId)) {
            return DecisaoSaga.ignorada();
        }
        reservaLiberada = true;
        prazoExpiracao = null;
        situacao = SituacaoSaga.COMPENSADA;
        falhaCompensacao = null;
        return new DecisaoSaga(true, StatusOrdemServico.CANCELADA, List.of());
    }

    private DecisaoSaga iniciarCompensacaoReserva(Instant agora, Duration prazoCompensacao, String detalhe) {
        etapaAtual = EtapaSaga.COMPENSANDO_RESERVA;
        situacao = SituacaoSaga.COMPENSANDO;
        tentativas = 1;
        falhaCompensacao = null;
        prazoExpiracao = agora.plus(prazoCompensacao);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (reservaId != null) {
            payload.put("reservaId", reservaId);
        }
        payload.put("motivo", motivoCompensacao.name());
        if (detalhe != null) {
            payload.put("detalhe", detalhe);
        }
        return new DecisaoSaga(true, null, List.of(new ComandoSaga("LiberarReservaPecas", payload)));
    }

    private ComandoSaga comandoCancelarCobranca() {
        return new ComandoSaga(
                "CancelarCobranca",
                Map.of(
                        "cobrancaId", valorOuVazio(cobrancaId),
                        "motivo", motivoCompensacao.name()));
    }

    private ComandoSaga comandoEstornarPagamento() {
        return new ComandoSaga(
                "EstornarPagamento",
                Map.of(
                        "cobrancaId", valorOuVazio(cobrancaId),
                        "motivo", motivoCompensacao.name()));
    }

    private ComandoSaga comandoCancelarExecucao() {
        return new ComandoSaga(
                "CancelarExecucao",
                Map.of(
                        "reservaId", valorOuVazio(reservaId),
                        "motivo", motivoCompensacao.name()));
    }

    private ComandoSaga comandoLiberarReserva() {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (reservaId != null) {
            payload.put("reservaId", reservaId);
        }
        payload.put("motivo", motivoCompensacao.name());
        return new ComandoSaga("LiberarReservaPecas", payload);
    }

    private List<ComandoSaga> comandosCompensacao() {
        return switch (etapaAtual) {
            case COMPENSANDO_COBRANCA -> cobrancaId == null ? List.of() : List.of(comandoCancelarCobranca());
            case COMPENSANDO_EXECUCAO -> List.of(comandoCancelarExecucao());
            case COMPENSANDO_ESTORNO -> List.of(comandoEstornarPagamento());
            case COMPENSANDO_RESERVA -> List.of(comandoLiberarReserva());
            default -> List.of();
        };
    }

    private void registrar(EventoSaga evento, boolean aplicado) {
        historico.add(new EventoSagaRegistrado(
                evento.messageId(), evento.tipo(), evento.ocorridoEm(), aplicado, detalhe(evento.payload())));
    }

    private static String detalhe(Map<String, Object> payload) {
        String detalhe = textoOpcional(payload, "motivo");
        if (detalhe == null) {
            detalhe = textoOpcional(payload, "observacao");
        }
        return detalhe == null ? null : detalhe.substring(0, Math.min(detalhe.length(), 255));
    }

    private static String texto(Map<String, Object> payload, String campo) {
        String valor = textoOpcional(payload, campo);
        if (valor == null) {
            throw new IllegalArgumentException("Campo obrigatório ausente no evento da Saga: " + campo);
        }
        return valor;
    }

    private static String textoOpcional(Map<String, Object> payload, String campo) {
        Object valor = payload.get(campo);
        return valor instanceof String texto && !texto.isBlank() ? texto : null;
    }

    private static BigDecimal decimal(Map<String, Object> payload, String campo) {
        Object valor = payload.get(campo);
        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }
        if (valor instanceof Number numero) {
            return new BigDecimal(numero.toString());
        }
        throw new IllegalArgumentException("Campo monetário inválido no evento da Saga: " + campo);
    }

    private static Instant instante(String valor) {
        try {
            return Instant.parse(valor);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Instante inválido no evento da Saga: " + valor, e);
        }
    }

    private static String valorOuVazio(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Identificador obrigatório da compensação não foi registrado.");
        }
        return valor;
    }

    public String getOsId() {
        return osId;
    }

    public boolean isDiagnosticoSolicitado() {
        return diagnosticoSolicitado;
    }

    public EtapaSaga getEtapaAtual() {
        return etapaAtual;
    }

    public SituacaoSaga getSituacao() {
        return situacao;
    }

    public MotivoCompensacao getMotivoCompensacao() {
        return motivoCompensacao;
    }

    public String getReservaId() {
        return reservaId;
    }

    public String getOrcamentoId() {
        return orcamentoId;
    }

    public BigDecimal getValorOrcamento() {
        return valorOrcamento;
    }

    public String getCobrancaId() {
        return cobrancaId;
    }

    public String getPagamentoIdExterno() {
        return pagamentoIdExterno;
    }

    public boolean isReservaLiberada() {
        return reservaLiberada;
    }

    public Instant getPrazoExpiracao() {
        return prazoExpiracao;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getFalhaCompensacao() {
        return falhaCompensacao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public List<EventoSagaRegistrado> getHistorico() {
        return List.copyOf(historico);
    }
}
