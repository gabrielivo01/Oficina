package io.github.gabrielivo.oficina.domain.saga;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EventoSaga(String messageId, String tipo, Instant ocorridoEm, Map<String, Object> payload) {

    public EventoSaga {
        if (tipo == null || tipo.isBlank() || ocorridoEm == null || payload == null) {
            throw new IllegalArgumentException("Tipo, instante e payload do evento da Saga são obrigatórios.");
        }
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
