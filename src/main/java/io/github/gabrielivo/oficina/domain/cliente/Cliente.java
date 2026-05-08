package io.github.gabrielivo.oficina.domain.cliente;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;



@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @Column(length = 36)
    private String id;
    
    @Column(length = 11, unique = true, nullable = false)
    private String cpf;

    @Column(length = 150, nullable = false)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    public Cliente() {}

    public Cliente(String cpf, String nome, String telefone, Endereco endereco) {
        validar(cpf, nome);
        this.id = UUID.randomUUID().toString();
        this.cpf = cpf;
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
    }

    private void validar(String cpf, String nome) {
        if (cpf == null || cpf.isBlank()) throw new IllegalArgumentException("CPF é obrigatório.");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome é obrigatório.");
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

    public void atualizar(String nome, String telefone, Endereco endereco) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome é obrigatório.");
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
    }

    // Getters
    public String getId() { return id; }
    public String getCpf() { return cpf; }
    public String getNome() { return nome; }
    public String getTelefone() { return telefone; }
    public Endereco getEndereco() { return endereco; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
