package io.github.gabrielivo.oficina.domain.ordemServico;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ordem_servico")
public class OrdemServico {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusOrdemServico status;

    @Column(name = "valor_total", precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemOrdemServico> itens = new ArrayList<>();

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "status_desde")
    private LocalDateTime statusDesde;

    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequencia ASC")
    private List<HistoricoStatusOrdemServico> historico = new ArrayList<>();

    /** Eventos de domínio ainda não extraídos pelo service (não persistidos). */
    @Transient
    private final List<StatusOrdemServicoAlterado> eventosPendentes = new ArrayList<>();

    public OrdemServico() {}

    public OrdemServico(Cliente cliente, Veiculo veiculo) {
        this.id = UUID.randomUUID().toString();
        this.cliente = cliente;
        this.veiculo = veiculo;
        this.status = StatusOrdemServico.RECEBIDA;
        this.valorTotal = BigDecimal.ZERO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
        this.statusDesde = this.criadoEm;
        registrarHistorico(null, OrigemMudancaStatus.USUARIO, "Abertura da OS", this.criadoEm);
    }

    /**
     * Único ponto de mudança de status do agregado: valida a transição (seção 3.1), atualiza {@code statusDesde},
     * grava a linha em {@code historico_status_os} e registra o evento {@link StatusOrdemServicoAlterado}. Os demais
     * métodos de negócio delegam para cá.
     */
    public void alterarStatus(StatusOrdemServico novoStatus, OrigemMudancaStatus origem, String motivo) {
        if (origem == null) {
            throw new IllegalArgumentException("Origem da mudança de status é obrigatória.");
        }
        if (motivo != null && motivo.length() > HistoricoStatusOrdemServico.TAMANHO_MAXIMO_MOTIVO) {
            throw new IllegalArgumentException(
                    "Motivo deve ter no máximo " + HistoricoStatusOrdemServico.TAMANHO_MAXIMO_MOTIVO + " caracteres.");
        }
        validarTransicaoPara(novoStatus);
        StatusOrdemServico anterior = this.status;
        this.status = novoStatus;
        LocalDateTime agora = LocalDateTime.now();
        this.atualizadoEm = agora;
        this.statusDesde = agora;
        registrarHistorico(anterior, origem, motivo, agora);
        this.eventosPendentes.add(new StatusOrdemServicoAlterado(this.id, anterior, novoStatus, origem, motivo, agora));
    }

    /** Devolve e limpa os eventos registrados desde a última extração (na ordem em que ocorreram). */
    public List<StatusOrdemServicoAlterado> extrairEventos() {
        List<StatusOrdemServicoAlterado> eventos = List.copyOf(eventosPendentes);
        eventosPendentes.clear();
        return eventos;
    }

    public void entregar() {
        if (this.status != StatusOrdemServico.FINALIZADA) {
            throw new OrdemServicoException("Somente uma OS finalizada pode ser entregue.");
        }
        alterarStatus(StatusOrdemServico.ENTREGUE, OrigemMudancaStatus.USUARIO, "Entrega ao cliente");
    }

    /** Cancela a OS; permitido em qualquer status antes de FINALIZADA. */
    public void cancelar(OrigemMudancaStatus origem, String motivo) {
        if (!this.status.podeTransicionarPara(StatusOrdemServico.CANCELADA)) {
            throw new OrdemServicoException("Não é possível cancelar uma OS com status " + this.status + ".");
        }
        alterarStatus(
                StatusOrdemServico.CANCELADA,
                origem,
                motivo == null || motivo.isBlank() ? "Cancelamento solicitado" : motivo.strip());
    }

    private void registrarHistorico(
            StatusOrdemServico anterior, OrigemMudancaStatus origem, String motivo, LocalDateTime quando) {
        this.historico.add(new HistoricoStatusOrdemServico(
                this, this.historico.size() + 1, anterior, this.status, quando, origem, motivo));
    }

    public void registrarSnapshotDiagnostico(List<ItemOrdemServico> itens) {
        if (this.status != StatusOrdemServico.EM_DIAGNOSTICO) {
            throw new OrdemServicoException("Os itens do diagnóstico só podem ser registrados durante o diagnóstico.");
        }
        if (!this.itens.isEmpty()) {
            throw new OrdemServicoException("O snapshot do diagnóstico é imutável depois de registrado.");
        }
        if (itens == null || itens.isEmpty()) {
            throw new OrdemServicoException("O diagnóstico deve conter pelo menos um item.");
        }
        for (ItemOrdemServico item : itens) {
            if (item.getOrdemServico() != this) {
                throw new OrdemServicoException("Todos os itens do snapshot devem pertencer à OS.");
            }
        }
        this.itens.addAll(itens);
        recalcularTotal();
        marcarAtualizacao();
    }

    public void recalcularTotal() {
        this.valorTotal = itens.stream().map(ItemOrdemServico::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validarTransicaoPara(StatusOrdemServico proximoStatus) {
        if (proximoStatus == null || !this.status.podeTransicionarPara(proximoStatus)) {
            throw new OrdemServicoException("Transição inválida de " + this.status + " para " + proximoStatus);
        }
    }

    private void marcarAtualizacao() {
        this.atualizadoEm = LocalDateTime.now();
    }

    // Getters
    public String getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public StatusOrdemServico getStatus() {
        return status;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public List<ItemOrdemServico> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public LocalDateTime getStatusDesde() {
        return statusDesde;
    }

    public List<HistoricoStatusOrdemServico> getHistorico() {
        return Collections.unmodifiableList(historico);
    }
}
