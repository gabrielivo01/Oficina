package io.github.gabrielivo.oficina.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gabrielivo.oficina.domain.cliente.Cliente;
import io.github.gabrielivo.oficina.domain.cliente.ClienteRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Aceite da etapa 2.1: a aplicação sobe num PostgreSQL vazio só com as migrations do Flyway e o Hibernate valida o
 * mapeamento ({@code ddl-auto=validate}) contra o schema criado por elas.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class MigracoesPostgresTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    void deveCriarSchemaSoComAsMigrationsDoFlyway() {
        List<String> versoes = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versoes).startsWith("1");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.tables where table_name = 'ordem_servico'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void devePersistirEntidadeNoSchemaMigrado() {
        Cliente salvo = clienteRepository.save(new Cliente("52998224725", "Cliente Flyway", null, null));

        assertThat(clienteRepository.findByCpf("52998224725"))
                .get()
                .extracting(Cliente::getId)
                .isEqualTo(salvo.getId());
    }
}
