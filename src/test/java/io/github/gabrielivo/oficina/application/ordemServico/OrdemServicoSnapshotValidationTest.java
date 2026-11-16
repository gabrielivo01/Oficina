package io.github.gabrielivo.oficina.application.ordemServico;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoException;
import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdemServicoSnapshotValidationTest {

    @Test
    void deveRejeitarDiagnosticoVazio() {
        OrdemServico os = ordemEmDiagnostico();

        assertThrows(OrdemServicoException.class, () -> os.registrarSnapshotDiagnostico(List.of()));
    }

    @Test
    void deveRejeitarDiagnosticoNulo() {
        OrdemServico os = ordemEmDiagnostico();

        assertThrows(OrdemServicoException.class, () -> os.registrarSnapshotDiagnostico(null));
    }

    private OrdemServico ordemEmDiagnostico() {
        Cliente cliente = new Cliente("11122233344", "Cliente", null, "cliente@example.com", null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        OrdemServico os = new OrdemServico(cliente, veiculo);
        os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, OrigemMudancaStatus.SAGA, "DiagnosticoIniciado");
        return os;
    }
}
