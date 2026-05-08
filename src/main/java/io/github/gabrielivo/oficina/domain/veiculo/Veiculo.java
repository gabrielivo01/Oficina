package io.github.gabrielivo.oficina.domain.veiculo;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "veiculo")
public class Veiculo {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(length = 10, unique = true, nullable = false)
    private String placa;

    @Column(length = 100, nullable = false)
    private String marca;

    @Column(length = 100, nullable = false)
    private String modelo;

    @Column
    private Integer ano;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    public Veiculo() {}

    public Veiculo(Cliente cliente, String placa, String marca, String modelo, Integer ano) {
        validar(placa, marca, modelo);
        this.id = UUID.randomUUID().toString();
        this.cliente = cliente;
        this.placa = placa;
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
    }

    private void validar(String placa, String marca, String modelo) {
        if (placa == null || placa.isBlank()) throw new IllegalArgumentException("Placa é obrigatória.");
        if (marca == null || marca.isBlank()) throw new IllegalArgumentException("Marca é obrigatória.");
        if (modelo == null || modelo.isBlank()) throw new IllegalArgumentException("Modelo é obrigatório.");
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID().toString();
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public void atualizar(String marca, String modelo, Integer ano) {
        if (marca == null || marca.isBlank()) throw new IllegalArgumentException("Marca é obrigatória.");
        if (modelo == null || modelo.isBlank()) throw new IllegalArgumentException("Modelo é obrigatório.");
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
    }

    // Getters
    public String getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public String getPlaca() { return placa; }
    public String getMarca() { return marca; }
    public String getModelo() { return modelo; }
    public Integer getAno() { return ano; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
