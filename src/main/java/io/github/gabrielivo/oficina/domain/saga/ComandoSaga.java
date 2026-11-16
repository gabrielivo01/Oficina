package io.github.gabrielivo.oficina.domain.saga;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ComandoSaga(String tipo, Map<String, Object> payload) {

    public ComandoSaga {
        if (tipo == null || tipo.isBlank() || payload == null) {
            throw new IllegalArgumentException("Tipo e payload do comando da Saga são obrigatórios.");
        }
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
