package io.github.gabrielivo.oficina.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gabrielivo.oficina.application.ordemServico.AbrirOrdemServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.ListarOrdensServicoCommand;
import io.github.gabrielivo.oficina.application.ordemServico.OrdemServicoService;
import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import io.github.gabrielivo.oficina.domain.ordemServico.OrdemServico;
import io.github.gabrielivo.oficina.domain.ordemServico.StatusOrdemServico;
import io.github.gabrielivo.oficina.domain.veiculo.Veiculo;
import io.github.gabrielivo.oficina.domain.veiculo.VeiculoRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Aceite da etapa 2.1: a aplicação sobe num PostgreSQL vazio só com as migrations do Flyway e o Hibernate valida o
 * mapeamento ({@code ddl-auto=validate}) contra o schema criado por elas. Também exercita no PostgreSQL real o que os
 * testes legados só cobrem em H2 (check de status, histórico e consulta paginada).
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class MigracoesPostgresTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private OrdemServicoService ordemServicoService;

    @Test
    void deveCriarSchemaSoComAsMigrationsDoFlyway() {
        List<String> versoes = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versoes).startsWith("1", "2");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.tables where table_name in"
                                + " ('ordem_servico', 'historico_status_os')",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    void devePersistirEntidadeNoSchemaMigrado() {
        Cliente salvo = clienteRepository.save(new Cliente("52998224725", "Cliente Flyway", null, null));

        assertThat(clienteRepository.findByCpf("52998224725"))
                .get()
                .extracting(Cliente::getId)
                .isEqualTo(salvo.getId());
    }

    @Test
    void deveCancelarRegistrarHistoricoEListarPaginadoNoPostgres() {
        Cliente cliente = clienteRepository.save(new Cliente("11144477735", "Cliente Saga", null, null));
        Veiculo veiculo = veiculoRepository.save(new Veiculo(cliente, "PGS1234", "Fiat", "Uno", 2020));
        OrdemServico aberta = ordemServicoService.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId()));
        OrdemServico cancelada =
                ordemServicoService.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId()));

        ordemServicoService.cancelar(cancelada.getId(), "Cliente desistiu");

        assertThat(ordemServicoService.listarHistorico(cancelada.getId()))
                .extracting(h -> h.getStatusNovo())
                .containsExactly(StatusOrdemServico.RECEBIDA, StatusOrdemServico.CANCELADA);
        assertThat(ordemServicoService
                        .listar(new ListarOrdensServicoCommand(Set.of(StatusOrdemServico.CANCELADA), 0, 10))
                        .getContent())
                .extracting(OrdemServico::getId)
                .contains(cancelada.getId())
                .doesNotContain(aberta.getId());
        assertThat(ordemServicoService
                        .listar(new ListarOrdensServicoCommand(null, 0, 10))
                        .getContent())
                .extracting(OrdemServico::getId)
                .contains(aberta.getId())
                .doesNotContain(cancelada.getId());
    }
}
