package io.github.gabrielivo.oficina.saga.memoria;

import io.github.gabrielivo.oficina.application.mensageria.EnvelopeMensagem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Monta eventos a partir dos exemplos do catálogo ({@code src/test/resources/contratos/}, cópia de
 * {@code documentacao/contratos_mensagens.md}). Os participantes falsos respondem com esses exemplos, trocando só os
 * identificadores (mensagem, OS e ids de reserva/orçamento/cobrança) e o instante: assim a Saga em memória exercita o
 * mesmo formato que os serviços reais publicam.
 */
public final class CatalogoContratos {

    private final JsonMapper jsonMapper;

    public CatalogoContratos(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param tipo nome do evento (= nome do arquivo de exemplo)
     * @param osId OS (e Saga) a que o evento se refere
     * @param campos campos do payload a substituir no exemplo
     */
    public EnvelopeMensagem evento(String tipo, String osId, Map<String, Object> campos) {
        ObjectNode payload = (ObjectNode) exemplo(tipo).get("payload").deepCopy();
        campos.forEach((campo, valor) -> payload.set(campo, jsonMapper.valueToTree(valor)));
        return new EnvelopeMensagem(UUID.randomUUID().toString(), tipo, osId, osId, Instant.now(), payload);
    }

    private JsonNode exemplo(String tipo) {
        try (InputStream in = CatalogoContratos.class.getResourceAsStream("/contratos/" + tipo + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("Evento sem exemplo no catálogo de contratos: " + tipo);
            }
            return jsonMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
