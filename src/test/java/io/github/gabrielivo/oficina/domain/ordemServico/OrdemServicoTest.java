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

    /** Leva a OS do status inicial até o status pedido pelo caminho feliz (avanço manual). */
    private static OrdemServico osEm(StatusOrdemServico alvo) {
        OrdemServico os = novaOs();
        if (alvo == StatusOrdemServico.CANCELADA) {
            os.cancelar(OrigemMudancaStatus.USUARIO, "teste");
            return os;
        }
        while (os.getStatus() != alvo) {
            os.avancarStatus();
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
    void deveAdicionarItemERecalcularTotal() {
        OrdemServico os = novaOs();

        ItemOrdemServico item = new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("100.00"));
        os.adicionarItem(item);

        assertEquals(1, os.getItens().size());
        assertEquals(new BigDecimal("100.00"), os.getValorTotal());
    }

    @Test
    void deveRemoverItemERecalcularTotal() {
        OrdemServico os = novaOs();

        ItemOrdemServico item = new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("100.00"));
        os.adicionarItem(item);
        os.removerItem(item.getId());

        assertTrue(os.getItens().isEmpty());
        assertEquals(BigDecimal.ZERO, os.getValorTotal());
    }

    @Test
    void deveLancarExcecaoAoRemoverItemInexistente() {
        OrdemServico os = novaOs();

        OrdemServicoException exception =
                assertThrows(OrdemServicoException.class, () -> os.removerItem("item-inexistente"));

        assertEquals("Item não encontrado na OS: item-inexistente", exception.getMessage());
    }

    @Test
    void deveAvancarStatusQuandoTransicaoValida() {
        OrdemServico os = novaOs();

        os.avancarStatus();

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoAoAvancarOsEntregue() {
        OrdemServico os = osEm(StatusOrdemServico.ENTREGUE);

        OrdemServicoException exception = assertThrows(OrdemServicoException.class, os::avancarStatus);

        assertTrue(exception.getMessage().contains("Transição inválida"));
    }

    @Test
    void deveIrParaAguardandoPagamentoQuandoOrcamentoAprovado() {
        OrdemServico os = osEm(StatusOrdemServico.AGUARDANDO_APROVACAO);

        os.aprovarOrcamento();

        assertEquals(StatusOrdemServico.AGUARDANDO_PAGAMENTO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoAoAprovarOrcamentoForaDeAguardandoAprovacao() {
        OrdemServico os = novaOs();

        assertThrows(OrdemServicoException.class, os::aprovarOrcamento);
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());
    }

    @Test
    void deveManterStatusESemHistoricoNovoQuandoOrcamentoRecusado() {
        OrdemServico os = osEm(StatusOrdemServico.AGUARDANDO_APROVACAO);
        int registrosAntes = os.getHistorico().size();

        os.recusarOrcamento();

        assertEquals(StatusOrdemServico.AGUARDANDO_APROVACAO, os.getStatus());
        assertEquals(registrosAntes, os.getHistorico().size());
    }

    @Test
    void deveAtualizarStatusExternoQuandoTransicaoEhValida() {
        OrdemServico os = novaOs();

        os.atualizarStatusExterno(StatusOrdemServico.EM_DIAGNOSTICO);

        assertEquals(StatusOrdemServico.EM_DIAGNOSTICO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoQuandoStatusExternoNulo() {
        OrdemServico os = novaOs();

        OrdemServicoException exception =
                assertThrows(OrdemServicoException.class, () -> os.atualizarStatusExterno(null));

        assertEquals("Status é obrigatório.", exception.getMessage());
    }

    @Test
    void deveLancarExcecaoQuandoStatusExternoPulaEtapa() {
        OrdemServico os = novaOs();

        assertThrows(OrdemServicoException.class, () -> os.atualizarStatusExterno(StatusOrdemServico.EM_EXECUCAO));
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

        os.avancarStatus();
        os.atualizarStatusExterno(StatusOrdemServico.AGUARDANDO_APROVACAO);
        os.aprovarOrcamento();

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
                                OrigemMudancaStatus.USUARIO),
                        tuple(
                                3,
                                StatusOrdemServico.EM_DIAGNOSTICO,
                                StatusOrdemServico.AGUARDANDO_APROVACAO,
                                OrigemMudancaStatus.SISTEMA),
                        tuple(
                                4,
                                StatusOrdemServico.AGUARDANDO_APROVACAO,
                                StatusOrdemServico.AGUARDANDO_PAGAMENTO,
                                OrigemMudancaStatus.USUARIO));
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

    @Test
    void naoDevePermitirAdicionarItemEmOsCancelada() {
        OrdemServico os = osEm(StatusOrdemServico.CANCELADA);

        assertThrows(
                OrdemServicoException.class,
                () -> os.adicionarItem(new ItemOrdemServico(os, "Troca de óleo", new BigDecimal("10.00"))));
    }
}
