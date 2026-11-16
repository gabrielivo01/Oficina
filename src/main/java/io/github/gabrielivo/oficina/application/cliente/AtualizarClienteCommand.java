package io.github.gabrielivo.oficina.application.cliente;

public record AtualizarClienteCommand(String nome, String telefone, String email, EnderecoCommand endereco) {
    public AtualizarClienteCommand(String nome, String telefone, EnderecoCommand endereco) {
        this(nome, telefone, null, endereco);
    }
}
