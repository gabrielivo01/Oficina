package io.github.gabrielivo.oficina.presentation.ordemServico;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.gabrielivo.oficina.application.ordemServico.ListarOrdensServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServicoException;
import io.github.gabrielivo.oficina.domain.ordemServico.OrigemMudancaStatus;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.infrastructure.security.InternalApiKeyFilter;
import io.github.gabrielivo.oficina.infrastructure.security.JwtAuthFilter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoints de listagem paginada, histórico e cancelamento (etapa 2.2). Segurança fora do escopo deste teste. */
@WebMvcTest(
        controllers = OrdemServicoController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {JwtAuthFilter.class, InternalApiKeyFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import({OrdemServicoMapper.class, SagaMapper.class})
class OrdemServicoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrdemServicoService ordemServicoService;

    private static OrdemServico novaOs() {
        Cliente cliente = new Cliente("11122233344", "Gabriel", null, null);
        Veiculo veiculo = new Veiculo(cliente, "ABC1234", "Fiat", "Uno", 2020);
        return new OrdemServico(cliente, veiculo);
    }

    @Test
    void deveListarComFiltroDeStatusEPaginacao() throws Exception {
        OrdemServico os = novaOs();
        var esperado = new ListarOrdensServicoCommand(
                Set.of(StatusOrdemServico.RECEBIDA, StatusOrdemServico.EM_DIAGNOSTICO), 1, 5);
        when(ordemServicoService.listar(esperado)).thenReturn(new PageImpl<>(List.of(os), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/ordens-servico")
                        .param("status", "RECEBIDA", "EM_DIAGNOSTICO")
                        .param("pagina", "1")
                        .param("tamanho", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].id").value(os.getId()))
                .andExpect(jsonPath("$.conteudo[0].status").value("RECEBIDA"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamanho").value(5))
                .andExpect(jsonPath("$.totalElementos").value(6))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void deveListarAtivasNaPrimeiraPaginaPorPadrao() throws Exception {
        when(ordemServicoService.listar(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/ordens-servico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isEmpty());

        verify(ordemServicoService).listar(new ListarOrdensServicoCommand(StatusOrdemServico.ativos(), 0, 20));
    }

    @Test
    void deveResponder400ParaStatusInexistente() throws Exception {
        mockMvc.perform(get("/ordens-servico").param("status", "INEXISTENTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Valor inválido para o parâmetro 'status'."));
        verifyNoInteractions(ordemServicoService);
    }

    @Test
    void deveResponder400ParaTamanhoDePaginaInvalido() throws Exception {
        mockMvc.perform(get("/ordens-servico").param("tamanho", "0")).andExpect(status().isBadRequest());
        verifyNoInteractions(ordemServicoService);
    }

    @Test
    void deveRetornarHistoricoDeStatus() throws Exception {
        OrdemServico os = novaOs();
        os.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO, OrigemMudancaStatus.SAGA, "DiagnosticoIniciado");
        when(ordemServicoService.listarHistorico("os-1")).thenReturn(os.getHistorico());

        mockMvc.perform(get("/ordens-servico/os-1/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sequencia").value(1))
                .andExpect(jsonPath("$[0].statusAnterior").doesNotExist())
                .andExpect(jsonPath("$[0].statusNovo").value("RECEBIDA"))
                .andExpect(jsonPath("$[1].statusAnterior").value("RECEBIDA"))
                .andExpect(jsonPath("$[1].statusNovo").value("EM_DIAGNOSTICO"))
                .andExpect(jsonPath("$[1].origem").value("SAGA"))
                .andExpect(jsonPath("$[1].alteradoEm").exists());
    }

    @Test
    void deveResponder422AoConsultarHistoricoDeOsInexistente() throws Exception {
        when(ordemServicoService.listarHistorico("x"))
                .thenThrow(new OrdemServicoException("Ordem de Serviço não encontrada: x"));

        mockMvc.perform(get("/ordens-servico/x/historico"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.erro").value("Ordem de Serviço não encontrada: x"));
    }

    @Test
    void deveCancelarComMotivo() throws Exception {
        OrdemServico os = novaOs();
        os.cancelar(OrigemMudancaStatus.USUARIO, "Cliente desistiu");
        when(ordemServicoService.cancelar("os-1", "Cliente desistiu")).thenReturn(os);

        mockMvc.perform(post("/ordens-servico/os-1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente desistiu\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void deveCancelarSemCorpo() throws Exception {
        OrdemServico os = novaOs();
        os.cancelar(OrigemMudancaStatus.USUARIO, null);
        when(ordemServicoService.cancelar(eq("os-1"), isNull())).thenReturn(os);

        mockMvc.perform(post("/ordens-servico/os-1/cancelar"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void deveResponder422AoCancelarOsFinalizada() throws Exception {
        when(ordemServicoService.cancelar(eq("os-1"), any()))
                .thenThrow(new OrdemServicoException("Não é possível cancelar uma OS com status FINALIZADA."));

        mockMvc.perform(post("/ordens-servico/os-1/cancelar"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.erro").value("Não é possível cancelar uma OS com status FINALIZADA."));
    }

    @Test
    void deveResponder400QuandoMotivoMuitoLongo() throws Exception {
        mockMvc.perform(post("/ordens-servico/os-1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ordemServicoService);
    }
}
