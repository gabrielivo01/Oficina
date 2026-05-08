package io.github.gabrielivo.oficina.presentation.veiculo;

import io.github.gabrielivo.oficina.application.veiculo.*;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import org.springframework.stereotype.Component;

@Component
public class VeiculoMapper {

    public CriarVeiculoCommand toCommand(VeiculoRequest request) {
        return new CriarVeiculoCommand(
            request.clienteId(),
            request.placa(),
            request.marca(),
            request.modelo(),
            request.ano()
        );
    }

    public AtualizarVeiculoCommand toCommand(AtualizarVeiculoRequest request) {
        return new AtualizarVeiculoCommand(
            request.marca(),
            request.modelo(),
            request.ano()
        );
    }

    public VeiculoResponse toResponse(Veiculo veiculo) {
        return new VeiculoResponse(
            veiculo.getId(),
            veiculo.getCliente().getId(),
            veiculo.getCliente().getNome(),
            veiculo.getPlaca(),
            veiculo.getMarca(),
            veiculo.getModelo(),
            veiculo.getAno(),
            veiculo.getCriadoEm(),
            veiculo.getAtualizadoEm()
        );
    }
}
