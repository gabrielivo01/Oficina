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

    public OrdemServico() {}

    public OrdemServico(Cliente cliente, Veiculo veiculo) {
        this.id = UUID.randomUUID().toString();
        this.cliente = cliente;
        this.veiculo = veiculo;
        this.status = StatusOrdemServico.RECEBIDA;
        this.valorTotal = BigDecimal.ZERO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    public void avancarStatus() {
        StatusOrdemServico proximoStatus = this.status.proximoStatus();
        validarTransicaoPara(proximoStatus);
        this.status = proximoStatus;
        marcarAtualizacao();
    }

    public void aprovarOrcamento() {
        validarStatusParaRespostaOrcamento();
        this.status = StatusOrdemServico.EM_EXECUCAO;
        marcarAtualizacao();
    }

    public void recusarOrcamento() {
        validarStatusParaRespostaOrcamento();
        this.status = StatusOrdemServico.AGUARDANDO_APROVACAO;
        marcarAtualizacao();
    }

    public void atualizarStatusExterno(StatusOrdemServico novoStatus) {
        if (novoStatus == null) {
            throw new OrdemServicoException("Status é obrigatório.");
        }
        validarTransicaoPara(novoStatus);
        this.status = novoStatus;
        marcarAtualizacao();
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
        this.valorTotal = itens.stream()
            .map(ItemOrdemServico::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validarTransicaoPara(StatusOrdemServico proximoStatus) {
        if (proximoStatus == null || !this.status.podeTransicionarPara(proximoStatus)) {
            throw new OrdemServicoException("Transição inválida de " + this.status + " para " + proximoStatus);
        }
    }

    private void validarStatusParaRespostaOrcamento() {
        if (this.status != StatusOrdemServico.AGUARDANDO_APROVACAO) {
            throw new OrdemServicoException("A aprovação de orçamento só é permitida quando a OS está aguardando aprovação.");
        }
    }

    private void validarAlteracaoDeItens() {
        if (this.status == StatusOrdemServico.FINALIZADA || this.status == StatusOrdemServico.ENTREGUE) {
            throw new OrdemServicoException("Não é possível alterar itens em uma OS finalizada ou entregue.");
        }
    }

    private void marcarAtualizacao() {
        this.atualizadoEm = LocalDateTime.now();
    }

    // Getters
    public String getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public Veiculo getVeiculo() { return veiculo; }
    public StatusOrdemServico getStatus() { return status; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public List<ItemOrdemServico> getItens() { return Collections.unmodifiableList(itens); }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}