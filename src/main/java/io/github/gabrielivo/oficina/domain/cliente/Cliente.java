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

    @Column(length = 254)
    private String email;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private StatusCliente status;

    public Cliente() {}

    public Cliente(String cpf, String nome, String telefone, Endereco endereco) {
        this(cpf, nome, telefone, null, endereco);
    }

    public Cliente(String cpf, String nome, String telefone, String email, Endereco endereco) {
        validar(cpf, nome);
        email = normalizarEmail(email);
        validarEmail(email);
        this.id = UUID.randomUUID().toString();
        this.cpf = cpf;
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
        this.endereco = endereco;
        this.status = StatusCliente.ATIVO;
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
        atualizar(nome, telefone, email, endereco);
    }

    public void atualizar(String nome, String telefone, String email, Endereco endereco) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome é obrigatório.");
        email = email == null ? this.email : normalizarEmail(email);
        validarEmail(email);
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
        this.endereco = endereco;
    }

    public void inativar() {
        this.status = StatusCliente.INATIVO;
    }

    public void reativar() {
        this.status = StatusCliente.ATIVO;
    }

    public boolean isAtivo() {
        return status == StatusCliente.ATIVO;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getCpf() {
        return cpf;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEmail() {
        return email;
    }

    public Endereco getEndereco() {
        return endereco;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public StatusCliente getStatus() {
        return status;
    }

    private static void validarEmail(String email) {
        if (email != null && !email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
    }

    private static String normalizarEmail(String email) {
        return email == null || email.isBlank() ? null : email.strip();
    }
}
