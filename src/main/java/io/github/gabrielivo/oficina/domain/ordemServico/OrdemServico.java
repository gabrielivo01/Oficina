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
        StatusOrdemServico[] valores = StatusOrdemServico.values();
        int proximoIndice = this.status.ordinal() + 1;

        if (proximoIndice >= valores.length) {
            throw new OrdemServicoException(
                "Transição inválida de " + this.status + " para nenhum estado posterior"
            );
        }

        StatusOrdemServico proximo = valores[proximoIndice];

        if (!this.status.podeTransicionarPara(proximo)) {
            throw new OrdemServicoException(
                "Transição inválida de " + this.status + " para " + proximo
            );
        }

        this.status = proximo;
        this.atualizadoEm = LocalDateTime.now();
    }

    public void limparItens() {
        this.itens.clear();
        recalcularTotal();
        this.atualizadoEm = LocalDateTime.now();
    }

    public void adicionarItem(ItemOrdemServico item) {
        if (this.status == StatusOrdemServico.FINALIZADA || this.status == StatusOrdemServico.ENTREGUE) {
            throw new OrdemServicoException("Não é possível adicionar itens em uma OS finalizada ou entregue.");
        }
        this.itens.add(item);
        recalcularTotal();
        this.atualizadoEm = LocalDateTime.now();
    }

    public void removerItem(String itemId) {
        boolean removido = this.itens.removeIf(i -> i.getId().equals(itemId));
        if (!removido) {
            throw new OrdemServicoException("Item não encontrado na OS: " + itemId);
        }
        recalcularTotal();
        this.atualizadoEm = LocalDateTime.now();
    }

    public void recalcularTotal() {
        this.valorTotal = itens.stream()
            .map(ItemOrdemServico::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
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