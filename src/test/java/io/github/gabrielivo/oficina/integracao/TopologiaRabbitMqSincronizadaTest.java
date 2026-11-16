package io.github.gabrielivo.oficina.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A topologia usada nos testes é cópia da fonte única ({@code oficina-infra-k8s}). Quando o repositório irmão está
 * presente (dev local), as duas precisam ser iguais; no CI, sem o irmão, o teste é ignorado.
 */
class TopologiaRabbitMqSincronizadaTest {

    private static final Path FONTE = Path.of("../oficina-infra-k8s/local/rabbitmq/definitions.json");

    @Test
    void copiaDeTesteDeveSerIgualAFonteUnicaDaTopologia() throws IOException {
        assumeTrue(Files.exists(FONTE), "oficina-infra-k8s não está ao lado deste repositório");
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode fonte = mapper.readTree(Files.readString(FONTE));
        JsonNode copia;
        try (InputStream in = getClass().getResourceAsStream("/rabbitmq/definitions.json")) {
            copia = mapper.readTree(in);
        }

        assertThat(copia).isEqualTo(fonte);
    }
}
