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
     * Único ponto de mudança de status do agregado: valida a transição (seção 3.1), atualiza {@code statusDesde} e
     * grava a linha em {@code historico_status_os}. Os demais métodos de negócio delegam para cá.
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
    }

    public void avancarStatus() {
        alterarStatus(this.status.proximoStatus(), OrigemMudancaStatus.USUARIO, "Avanço manual de status");
    }

    /** Orçamento aprovado: a OS passa a aguardar o pagamento, que é cobrado antes da execução. */
    public void aprovarOrcamento() {
        validarStatusParaRespostaOrcamento();
        alterarStatus(StatusOrdemServico.AGUARDANDO_PAGAMENTO, OrigemMudancaStatus.USUARIO, "Orçamento aprovado");
    }

    /**
     * Recusa ainda sem efeito de status (a compensação que cancela a OS vem com a Saga, Fase 5): só renova
     * {@code statusDesde}. Como o status não muda, não há linha de histórico.
     */
    public void recusarOrcamento() {
        validarStatusParaRespostaOrcamento();
        LocalDateTime agora = LocalDateTime.now();
        this.atualizadoEm = agora;
        this.statusDesde = agora;
    }

    public void atualizarStatusExterno(StatusOrdemServico novoStatus) {
        if (novoStatus == null) {
            throw new OrdemServicoException("Status é obrigatório.");
        }
        alterarStatus(novoStatus, OrigemMudancaStatus.SISTEMA, "Status atualizado via integração externa");
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

    public void limparItens() {
        this.itens.clear();
        recalcularTotal();
        this.atualizadoEm = LocalDateTime.now();
    }

    public void adicionarItem(ItemOrdemServico item) {
        validarAlteracaoDeItens();
        this.itens.add(item);
        recalcularTotal();
        marcarAtualizacao();
    }

    public void removerItem(String itemId) {
        boolean removido = this.itens.removeIf(i -> i.getId().equals(itemId));
        if (!removido) {
            throw new OrdemServicoException("Item não encontrado na OS: " + itemId);
        }
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

    private void validarStatusParaRespostaOrcamento() {
        if (this.status != StatusOrdemServico.AGUARDANDO_APROVACAO) {
            throw new OrdemServicoException(
                    "A aprovação de orçamento só é permitida quando a OS está aguardando aprovação.");
        }
    }

    private void validarAlteracaoDeItens() {
        if (!this.status.isAtivo()) {
            throw new OrdemServicoException(
                    "Não é possível alterar itens em uma OS finalizada, entregue ou cancelada.");
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
