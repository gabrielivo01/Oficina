package io.github.gabrielivo.oficina.domain.peca;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "peca")
public class Peca {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 150, nullable = false)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "quantidade_estoque", nullable = false)
    private Integer quantidadeEstoque;

    @Column(name = "estoque_minimo", nullable = false)
    private Integer estoqueMinimo;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    public Peca() {
    }

    public Peca(String nome, String descricao, BigDecimal preco, Integer quantidadeEstoque) {
        this(nome, descricao, preco, quantidadeEstoque, 0); // Estoque mínimo padrão = 0
    }

    public Peca(String nome, String descricao, BigDecimal preco, Integer quantidadeEstoque, Integer estoqueMinimo) {
        validar(nome, preco, quantidadeEstoque, estoqueMinimo);
        this.id = UUID.randomUUID().toString();
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.quantidadeEstoque = quantidadeEstoque;
        this.estoqueMinimo = estoqueMinimo;
    }

    private void validar(String nome, BigDecimal preco, Integer quantidadeEstoque, Integer estoqueMinimo) {
        if (nome == null || nome.isBlank())
            throw new IllegalArgumentException("Nome da peça é obrigatório.");
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Preço deve ser maior que zero.");
        if (quantidadeEstoque == null || quantidadeEstoque < 0)
            throw new IllegalArgumentException("Quantidade em estoque não pode ser negativa.");
        if (estoqueMinimo == null || estoqueMinimo < 0)
            throw new IllegalArgumentException("Estoque mínimo não pode ser negativo.");
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null)
            this.id = UUID.randomUUID().toString();
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public void atualizar(String nome, String descricao, BigDecimal preco, Integer quantidadeEstoque) {
        atualizar(nome, descricao, preco, quantidadeEstoque, this.estoqueMinimo);
    }

    public void atualizar(String nome, String descricao, BigDecimal preco, Integer quantidadeEstoque,
            Integer estoqueMinimo) {
        validar(nome, preco, quantidadeEstoque, estoqueMinimo);
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.quantidadeEstoque = quantidadeEstoque;
        this.estoqueMinimo = estoqueMinimo;
    }

    public void reduzirEstoque(int quantidade) {
        validarEstoqueParaReducao(quantidade);
        this.quantidadeEstoque -= quantidade;
    }

    public void reporEstoque(int quantidade) {
        if (quantidade <= 0)
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        this.quantidadeEstoque += quantidade;
    }

    public boolean estoqueEstaBaixo() {
        return this.quantidadeEstoque <= this.estoqueMinimo;
    }

    public boolean estoqueEstaCritico() {
        return this.quantidadeEstoque <= (this.estoqueMinimo / 2);
    }

    public void validarEstoqueParaReducao(int quantidade) {
        if (quantidade <= 0)
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        if (this.quantidadeEstoque < quantidade) {
            throw new IllegalStateException("Estoque insuficiente para a peça '" + nome + "'. Disponível: "
                    + quantidadeEstoque + ", solicitado: " + quantidade);
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public Integer getQuantidadeEstoque() {
        return quantidadeEstoque;
    }

    public Integer getEstoqueMinimo() {
        return estoqueMinimo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
