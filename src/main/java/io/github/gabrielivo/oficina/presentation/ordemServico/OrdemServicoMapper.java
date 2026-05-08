package io.github.gabrielivo.oficina.presentation.ordemServico;

import java.util.List;

import io.github.gabrielivo.oficina.application.ordemServico.AbrirOrdemServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.AdicionarItemOSCommand;
import io.github.gabrielivo.oficina.domain.ordemServico.ItemOrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import org.springframework.stereotype.Component;

@Component
public class OrdemServicoMapper {

    public AbrirOrdemServicoCommand toCommand(OrdemServicoRequest request) {
        return new AbrirOrdemServicoCommand(request.clienteId(), request.veiculoId());
    }

    public AdicionarItemOSCommand toCommand(String osId, AdicionarItemOSRequest request) {
        return new AdicionarItemOSCommand(
            osId,
            request.descricao(),
            request.tipo(),
            request.valor(),
            request.quantidade(),
            request.pecaId()
        );
    }

    public OrdemServicoResponse toResponse(OrdemServico os) {
        List<ItemOSResponse> itens = os.getItens().stream()
            .map(this::toItemResponse)
            .toList();

        return new OrdemServicoResponse(
            os.getId(),
            os.getCliente().getId(),
            os.getCliente().getNome(),
            os.getVeiculo().getId(),
            os.getVeiculo().getPlaca(),
            os.getStatus(),
            os.getValorTotal(),
            itens,
            os.getCriadoEm(),
            os.getAtualizadoEm()
        );
    }

    private ItemOSResponse toItemResponse(ItemOrdemServico item) {
        return new ItemOSResponse(
            item.getId(),
            item.getDescricao(),
            item.getTipo(),
            item.getValor(),
            item.getQuantidade(),
            item.getValorTotal(),
            item.getPeca() != null ? item.getPeca().getId() : null,
            item.getPeca() != null ? item.getPeca().getNome() : null
        );
    }
}
