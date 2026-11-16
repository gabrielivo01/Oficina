package io.github.gabrielivo.oficina.domain.ordemServico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.*;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrdemServicoTest {

    private static OrdemServico novaOs() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        return new OrdemServico(cliente, veiculo);
    }

    /** Prepara o agregado por transições explícitas usadas como fixture de teste. */
    private static OrdemServico osEm(StatusOrdemServico alvo) {
        OrdemServico os = novaOs();
        if (alvo == StatusOrdemServico.CANCELADA) {
            os.cancelar(OrigemMudancaStatus.USUARIO, "teste");
            return os;
        }
        while (os.getStatus() != alvo) {
            os.alterarStatus(os.getStatus().proximoStatus(), OrigemMudancaStatus.SAGA, "fixture");
        }
        return os;
    }

    @Test
    void deveCriarOrdemServicoComStatusRecebidaETotalZero() {
        OrdemServico os = novaOs();

        assertNotNull(os.getId());
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());
        assertEquals(BigDecimal.ZERO, os.getValorTotal());
        assertTrue(os.getItens().isEmpty());
    }

    @Test
    void deveRegistrarSnapshotDeDiagnosticoUmaUnicaVez() {
        OrdemServico os = novaOs();
        ItemOrdemServico item = new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("100.00"));
        os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, OrigemMudancaStatus.SAGA, "fixture");
        os.registrarSnapshotDiagnostico(java.util.List.of(item));

        assertEquals(1, os.getItens().size());
        assertEquals(new BigDecimal("100.00"), os.getValorTotal());
        assertThrows(OrdemServicoException.class, () -> os.registrarSnapshotDiagnostico(java.util.List.of(item)));
    }

    @Test
    void deveAvancarStatusQuandoTransicaoValida() {
        OrdemServico os = novaOs();

        os.alterarStatus(os.getStatus().proximoStatus(), OrigemMudancaStatus.SAGA, "teste");

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoAoAvancarOsEntregue() {
        OrdemServico os = osEm(StatusOrdemServico.ENTREGUE);

        OrdemServicoException exception = assertThrows(
                OrdemServicoException.class,
                () -> os.alterarStatus(os.getStatus().proximoStatus(), OrigemMudancaStatus.SAGA, "teste"));

        assertTrue(exception.getMessage().contains("Transição inválida"));
    }

    @Test
    void deveIrParaAguardandoPagamentoQuandoOrcamentoAprovado() {
        OrdemServico os = osEm(StatusOrdemServico.AGUARDANDO_APROVACAO);

        os.alterarStatus(StatusOrdemServico.AGUARDANDO_PAGAMENTO, OrigemMudancaStatus.SAGA, "OrcamentoAprovado");

        assertEquals(StatusOrdemServico.AGUARDANDO_PAGAMENTO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoAoAprovarOrcamentoForaDeAguardandoAprovacao() {
        OrdemServico os = novaOs();

        assertThrows(
                OrdemServicoException.class,
                () -> os.alterarStatus(
                        StatusOrdemServico.AGUARDANDO_PAGAMENTO, OrigemMudancaStatus.SAGA, "OrcamentoAprovado"));
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());
    }

    @Test
    void deveAtualizarStatusExternoQuandoTransicaoEhValida() {
        OrdemServico os = novaOs();

        os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, OrigemMudancaStatus.SAGA, "DiagnosticoIniciado");

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoQuandoStatusExternoNulo() {
        OrdemServico os = novaOs();

        OrdemServicoException exception = assertThrows(
                OrdemServicoException.class, () -> os.alterarStatus(null, OrigemMudancaStatus.SAGA, "teste"));

        assertTrue(exception.getMessage().contains("Transição inválida"));
    }

    @Test
    void deveLancarExcecaoQuandoStatusExternoPulaEtapa() {
        OrdemServico os = novaOs();

        assertThrows(
                OrdemServicoException.class,
                () -> os.alterarStatus(StatusOrdemServico.EM_EXECUCAO, OrigemMudancaStatus.SAGA, "teste"));
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());
    }

    // ---------------------------------------------------------------- histórico

    @Test
    void deveRegistrarAberturaNoHistorico() {
        OrdemServico os = novaOs();

        assertThat(os.getHistorico()).singleElement().satisfies(h -> {
            assertThat(h.getStatusAnterior()).isNull();
            assertThat(h.getStatusNovo()).isEqualTo(StatusOrdemServico.RECEBIDA);
            assertThat(h.getOrigem()).isEqualTo(OrigemMudancaStatus.USUARIO);
            assertThat(h.getSequencia()).isEqualTo(1);
            assertThat(h.getAlteradoEm()).isNotNull();
            assertThat(h.getMotivo()).isEqualTo("Abertura da OS");
        });
    }

    @Test
    void deveRegistrarCadaTransicaoNoHistoricoEmOrdem() {
        OrdemServico os = novaOs();

        os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, OrigemMudancaStatus.SAGA, "fixture");
        os.alterarStatus(StatusOrdemServico.AGUARDANDO_APROVACAO, OrigemMudancaStatus.SAGA, "fixture");
        os.alterarStatus(StatusOrdemServico.AGUARDANDO_PAGAMENTO, OrigemMudancaStatus.SAGA, "OrcamentoAprovado");

        assertThat(os.getHistorico())
                .extracting(
                        HistoricoStatusOrdemServico::getSequencia,
                        HistoricoStatusOrdemServico::getStatusAnterior,
                        HistoricoStatusOrdemServico::getStatusNovo,
                        HistoricoStatusOrdemServico::getOrigem)
                .containsExactly(
                        tuple(1, null, StatusOrdemServico.RECEBIDA, OrigemMudancaStatus.USUARIO),
                        tuple(
                                2,
                                StatusOrdemServico.RECEBIDA,
                                StatusOrdemServico.EM_DIAGNOSTICO,
                                OrigemMudancaStatus.SAGA),
                        tuple(
                                3,
                                StatusOrdemServico.EM_DIAGNOSTICO,
                                StatusOrdemServico.AGUARDANDO_APROVACAO,
                                OrigemMudancaStatus.SAGA),
                        tuple(
                                4,
                                StatusOrdemServico.AGUARDANDO_APROVACAO,
                                StatusOrdemServico.AGUARDANDO_PAGAMENTO,
                                OrigemMudancaStatus.SAGA));
        assertThat(os.getStatusDesde()).isEqualTo(os.getHistorico().get(3).getAlteradoEm());
    }

    @Test
    void naoDeveRegistrarHistoricoQuandoTransicaoInvalida() {
        OrdemServico os = novaOs();

        assertThrows(
                OrdemServicoException.class,
                () -> os.alterarStatus(StatusOrdemServico.FINALIZADA, OrigemMudancaStatus.SAGA, "pulo"));

        assertThat(os.getHistorico()).hasSize(1);
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());
    }

    @Test
    void deveRegistrarOrigemEMotivoInformadosNaTransicao() {
        OrdemServico os = novaOs();

        os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, OrigemMudancaStatus.SAGA, "DiagnosticoIniciado");

        HistoricoStatusOrdemServico ultimo = os.getHistorico().get(1);
        assertEquals(OrigemMudancaStatus.SAGA, ultimo.getOrigem());
        assertEquals("DiagnosticoIniciado", ultimo.getMotivo());
    }

    @Test
    void deveExigirOrigemNaTransicao() {
        OrdemServico os = novaOs();

        assertThrows(
                IllegalArgumentException.class, () -> os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, null, "x"));
    }

    // ---------------------------------------------------------------- cancelamento

    @ParameterizedTest
    @EnumSource(
            value = StatusOrdemServico.class,
            names = {"RECEBIDA", "EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "AGUARDANDO_PAGAMENTO", "EM_EXECUCAO"})
    void deveCancelarOsAntesDeFinalizadaERegistrarMotivo(StatusOrdemServico statusAtual) {
        OrdemServico os = osEm(statusAtual);

        os.cancelar(OrigemMudancaStatus.USUARIO, "Cliente desistiu");

        assertEquals(StatusOrdemServico.CANCELADA, os.getStatus());
        HistoricoStatusOrdemServico ultimo = os.getHistorico().getLast();
        assertEquals(statusAtual, ultimo.getStatusAnterior());
        assertEquals(StatusOrdemServico.CANCELADA, ultimo.getStatusNovo());
        assertEquals("Cliente desistiu", ultimo.getMotivo());
    }

    @ParameterizedTest
    @EnumSource(
            value = StatusOrdemServico.class,
            names = {"FINALIZADA", "ENTREGUE", "CANCELADA"})
    void naoDeveCancelarOsFinalizadaEntregueOuJaCancelada(StatusOrdemServico statusAtual) {
        OrdemServico os = osEm(statusAtual);
        int registrosAntes = os.getHistorico().size();

        OrdemServicoException exception = assertThrows(
                OrdemServicoException.class, () -> os.cancelar(OrigemMudancaStatus.USUARIO, "tarde demais"));

        assertThat(exception.getMessage()).contains("Não é possível cancelar");
        assertEquals(statusAtual, os.getStatus());
        assertEquals(registrosAntes, os.getHistorico().size());
    }

    @Test
    void deveUsarMotivoPadraoQuandoCancelamentoSemMotivo() {
        OrdemServico os = novaOs();

        os.cancelar(OrigemMudancaStatus.USUARIO, "  ");

        assertEquals("Cancelamento solicitado", os.getHistorico().getLast().getMotivo());
    }

    // ---------------------------------------------------------------- eventos de domínio

    @Test
    void naoDeveRegistrarEventoNaAbertura() {
        assertThat(novaOs().extrairEventos()).isEmpty();
    }

    @Test
    void deveRegistrarUmEventoPorTransicaoEEsvaziarAoExtrair() {
        OrdemServico os = novaOs();

        os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, OrigemMudancaStatus.SAGA, "teste");
        os.cancelar(OrigemMudancaStatus.SAGA, "Compensação");

        assertThat(os.extrairEventos())
                .extracting(
                        StatusOrdemServicoAlterado::ordemServicoId,
                        StatusOrdemServicoAlterado::statusAnterior,
                        StatusOrdemServicoAlterado::statusNovo,
                        StatusOrdemServicoAlterado::origem,
                        StatusOrdemServicoAlterado::motivo)
                .containsExactly(
                        tuple(
                                os.getId(),
                                StatusOrdemServico.RECEBIDA,
                                StatusOrdemServico.EM_DIAGNOSTICO,
                                OrigemMudancaStatus.SAGA,
                                "teste"),
                        tuple(
                                os.getId(),
                                StatusOrdemServico.EM_DIAGNOSTICO,
                                StatusOrdemServico.CANCELADA,
                                OrigemMudancaStatus.SAGA,
                                "Compensação"));
        assertThat(os.extrairEventos()).isEmpty();
    }

    @Test
    void naoDeveRegistrarEventoQuandoTransicaoInvalida() {
        OrdemServico os = osEm(StatusOrdemServico.AGUARDANDO_APROVACAO);
        os.extrairEventos();

        assertThrows(
                OrdemServicoException.class,
                () -> os.alterarStatus(StatusOrdemServico.ENTREGUE, OrigemMudancaStatus.SAGA, "teste"));

        assertThat(os.extrairEventos()).isEmpty();
    }

    @Test
    void naoDeveRegistrarSnapshotEmOsCancelada() {
        OrdemServico os = osEm(StatusOrdemServico.CANCELADA);

        assertThrows(
                OrdemServicoException.class,
                () -> os.registrarSnapshotDiagnostico(
                        java.util.List.of(new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("10.00")))));
    }
}
