package io.github.gabrielivo.oficina.presentation.cliente;

import io.github.gabrielivo.oficina.application.cliente.AtualizarClienteCommand;
import io.github.gabrielivo.oficina.application.cliente.ClienteStatusConsulta;
import io.github.gabrielivo.oficina.application.cliente.CriarClienteCommand;
import io.github.gabrielivo.oficina.application.cliente.EnderecoCommand;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.Endereco;
import org.springframework.stereotype.Component;

@Component
public class ClienteMapper {

    public CriarClienteCommand toCommand(ClienteRequest request) {
        EnderecoCommand endCmd = request.endereco() != null ? toEnderecoCommand(request.endereco()) : null;
        return new CriarClienteCommand(request.cpf(), request.nome(), request.telefone(), request.email(), endCmd);
    }

    public AtualizarClienteCommand toCommand(AtualizarClienteRequest request) {
        EnderecoCommand endCmd = request.endereco() != null ? toEnderecoCommand(request.endereco()) : null;
        return new AtualizarClienteCommand(request.nome(), request.telefone(), request.email(), endCmd);
    }

    public ClienteResponse toResponse(Cliente cliente) {
        EnderecoResponse endRes = cliente.getEndereco() != null ? toEnderecoResponse(cliente.getEndereco()) : null;
        return new ClienteResponse(
                cliente.getId(),
                cliente.getCpf(),
                cliente.getNome(),
                cliente.getTelefone(),
                cliente.getEmail(),
                endRes,
                cliente.getCriadoEm(),
                cliente.getAtualizadoEm(),
                cliente.getStatus());
    }

    public ClienteStatusResponse toStatusResponse(ClienteStatusConsulta consulta) {
        return new ClienteStatusResponse(consulta.existe(), consulta.ativo());
    }

    private EnderecoCommand toEnderecoCommand(EnderecoRequest req) {
        return new EnderecoCommand(
                req.cep(), req.logradouro(), req.numero(), req.complemento(), req.bairro(), req.cidade(), req.uf());
    }

    private EnderecoResponse toEnderecoResponse(Endereco e) {
        return new EnderecoResponse(
                e.getId(),
                e.getCep(),
                e.getLogradouro(),
                e.getNumero(),
                e.getComplemento(),
                e.getBairro(),
                e.getCidade(),
                e.getUf());
    }
}
