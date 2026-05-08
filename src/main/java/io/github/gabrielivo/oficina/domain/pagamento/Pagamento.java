package io.github.gabrielivo.oficina.domain.pagamento;

import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento")
public class Pagamento {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 30)
    private FormaPagamento formaPagamento;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    public Pagamento() {}

    public Pagamento(OrdemServico ordemServico, BigDecimal valor, FormaPagamento formaPagamento) {
        if (ordemServico == null) throw new IllegalArgumentException("Ordem de serviço é obrigatória.");
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Valor do pagamento deve ser maior que zero.");
        if (formaPagamento == null) throw new IllegalArgumentException("Forma de pagamento é obrigatória.");
        this.id = UUID.randomUUID().toString();
        this.ordemServico = ordemServico;
        this.valor = valor;
        this.formaPagamento = formaPagamento;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID().toString();
        this.criadoEm = LocalDateTime.now();
    }

    // Getters
    public String getId() { return id; }
    public OrdemServico getOrdemServico() { return ordemServico; }
    public BigDecimal getValor() { return valor; }
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
