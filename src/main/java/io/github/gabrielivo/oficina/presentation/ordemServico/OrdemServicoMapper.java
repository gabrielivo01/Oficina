package io.github.gabrielivo.oficina.presentation.ordemServico;

import io.github.gabrielivo.oficina.application.ordemServico.AbrirOrdemServicoCommand;
import io.github.gabrielivo.oficina.domain.ordemServico.HistoricoStatusOrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.ItemOrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.presentation.PaginaResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class OrdemServicoMapper {

    public AbrirOrdemServicoCommand toCommand(OrdemServicoRequest request) {
        return new AbrirOrdemServicoCommand(request.clienteId(), request.veiculoId());
    }

    public OrdemServicoResponse toResponse(OrdemServico os) {
        List<ItemOSResponse> itens =
                os.getItens().stream().map(this::toItemResponse).toList();

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
                os.getAtualizadoEm());
    }

    public PaginaResponse<OrdemServicoResponse> toResponse(Page<OrdemServico> pagina) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(this::toResponse).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }

    public List<HistoricoStatusResponse> toHistoricoResponse(List<HistoricoStatusOrdemServico> historico) {
        return historico.stream()
                .map(h -> new HistoricoStatusResponse(
                        h.getSequencia(),
                        h.getStatusAnterior(),
                        h.getStatusNovo(),
                        h.getAlteradoEm(),
                        h.getOrigem(),
                        h.getMotivo()))
                .toList();
    }

    private ItemOSResponse toItemResponse(ItemOrdemServico item) {
        return new ItemOSResponse(
                item.getId(),
                item.getDescricao(),
                item.getTipo(),
                item.getValor(),
                item.getQuantidade(),
                item.getValorTotal(),
                item.getPecaId(),
                item.getNomePeca());
    }
}
