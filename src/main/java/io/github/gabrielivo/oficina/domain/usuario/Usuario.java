package io.github.gabrielivo.oficina.domain.usuario;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String login;

    @Column(nullable = false)
    private String senha;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    public Usuario() {
    }

    public Usuario(String login, String senha) {
        this.id = UUID.randomUUID().toString();
        this.login = login;
        this.senha = senha;
        this.criadoEm = LocalDateTime.now();
    }

    public void atualizarSenha(String novaSenha) {
        this.senha = novaSenha;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getSenha() {
        return senha;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}