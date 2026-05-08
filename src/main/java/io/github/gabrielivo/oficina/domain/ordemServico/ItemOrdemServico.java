package io.github.gabrielivo.oficina.domain.ordemServico;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

import io.github.gabrielivo.oficina.domain.peca.Peca;

@Entity
@Table(name = "item_ordem_servico")
public class ItemOrdemServico {

     @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoItemOrdemServico tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column
    private Integer quantidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "peca_id")
    private Peca peca;

    public ItemOrdemServico() {}

    // Construtor para SERVICO
    public ItemOrdemServico(OrdemServico ordemServico, String descricao, BigDecimal valor) {
        this.id = UUID.randomUUID().toString();
        this.ordemServico = ordemServico;
        this.descricao = descricao;
        this.tipo = TipoItemOrdemServico.SERVICO;
        this.valor = valor;
        this.quantidade = 1;
    }

    // Construtor para PECA
    public ItemOrdemServico(OrdemServico ordemServico, String descricao, BigDecimal valor, Integer quantidade, Peca peca) {
        this.id = UUID.randomUUID().toString();
        this.ordemServico = ordemServico;
        this.descricao = descricao;
        this.tipo = TipoItemOrdemServico.PECA;
        this.valor = valor;
        this.quantidade = quantidade;
        this.peca = peca;
    }

    public BigDecimal getValorTotal() {
        return valor.multiply(BigDecimal.valueOf(quantidade));
    }

    // Getters
    public String getId() { return id; }
    public OrdemServico getOrdemServico() { return ordemServico; }
    public String getDescricao() { return descricao; }
    public TipoItemOrdemServico getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public Integer getQuantidade() { return quantidade; }
    public Peca getPeca() { return peca; }
}
