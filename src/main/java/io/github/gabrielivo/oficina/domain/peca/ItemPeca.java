package io.github.gabrielivo.oficina.domain.peca;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;

@Entity
@Table(name = "item_peca")
public class ItemPeca {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;

    @ManyToOne(optional = false)
    @JoinColumn(name = "peca_id", nullable = false)
    private Peca peca;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    public ItemPeca() {}

    public ItemPeca(OrdemServico ordemServico, Peca peca, Integer quantidade) {
        validar(ordemServico, peca, quantidade);
        this.id = UUID.randomUUID().toString();
        this.ordemServico = ordemServico;
        this.peca = peca;
        this.quantidade = quantidade;
        this.valor = peca.getPreco().multiply(BigDecimal.valueOf(quantidade));
    }

    private void validar(OrdemServico ordemServico, Peca peca, Integer quantidade) {
        if (ordemServico == null) throw new IllegalArgumentException("Ordem de serviço é obrigatória.");
        if (peca == null) throw new IllegalArgumentException("Peça é obrigatória.");
        if (quantidade == null || quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID().toString();
    }

    // Getters
    public String getId() { return id; }
    public OrdemServico getOrdemServico() { return ordemServico; }
    public Peca getPeca() { return peca; }
    public Integer getQuantidade() { return quantidade; }
    public BigDecimal getValor() { return valor; }
}
