package io.github.gabrielivo.oficina.domain.ordemServico;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

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

    @Column(name = "peca_id", length = 36)
    private String pecaId;

    @Column(name = "nome_peca", length = 150)
    private String nomePeca;

    public ItemOrdemServico() {}

    // Construtor para SERVICO
    public ItemOrdemServico(OrdemServico ordemServico, String descricao, BigDecimal valor) {
        validar(ordemServico, descricao, valor, 1);
        this.id = UUID.randomUUID().toString();
        this.ordemServico = ordemServico;
        this.descricao = descricao;
        this.tipo = TipoItemOrdemServico.SERVICO;
        this.valor = valor;
        this.quantidade = 1;
    }

    // Construtor para PECA
    public ItemOrdemServico(
            OrdemServico ordemServico,
            String descricao,
            BigDecimal valor,
            Integer quantidade,
            String pecaId,
            String nomePeca) {
        validar(ordemServico, descricao, valor, quantidade);
        if (pecaId == null || pecaId.isBlank()) {
            throw new IllegalArgumentException("pecaId é obrigatório para um item de peça.");
        }
        if (nomePeca == null || nomePeca.isBlank()) {
            throw new IllegalArgumentException("O nome da peça é obrigatório para manter o snapshot.");
        }
        this.id = UUID.randomUUID().toString();
        this.ordemServico = ordemServico;
        this.descricao = descricao;
        this.tipo = TipoItemOrdemServico.PECA;
        this.valor = valor;
        this.quantidade = quantidade;
        this.pecaId = pecaId;
        this.nomePeca = nomePeca;
    }

    private static void validar(OrdemServico ordemServico, String descricao, BigDecimal valor, Integer quantidade) {
        if (ordemServico == null || descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("OS e descrição do item são obrigatórias.");
        }
        if (valor == null || valor.signum() < 0 || quantidade == null || quantidade <= 0) {
            throw new IllegalArgumentException("Valor deve ser não negativo e quantidade deve ser positiva.");
        }
    }

    public BigDecimal getValorTotal() {
        return valor.multiply(BigDecimal.valueOf(quantidade));
    }

    // Getters
    public String getId() {
        return id;
    }

    public OrdemServico getOrdemServico() {
        return ordemServico;
    }

    public String getDescricao() {
        return descricao;
    }

    public TipoItemOrdemServico getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public String getPecaId() {
        return pecaId;
    }

    public String getNomePeca() {
        return nomePeca;
    }
}
