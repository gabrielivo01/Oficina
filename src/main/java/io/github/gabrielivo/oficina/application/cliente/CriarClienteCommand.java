package io.github.gabrielivo.oficina.application.cliente;

public record CriarClienteCommand(String cpf, String nome, String telefone, String email, EnderecoCommand endereco) {
    public CriarClienteCommand(String cpf, String nome, String telefone, EnderecoCommand endereco) {
        this(cpf, nome, telefone, null, endereco);
    }
}
