package io.github.gabrielivo.oficina.presentation.ordemServico;

import java.time.Instant;

public record SagaEventoResponse(String messageId, String tipo, Instant ocorridoEm, boolean aplicado, String detalhe) {}
