package io.github.gabrielivo.oficina.application.pagamento;

import io.github.gabrielivo.oficina.domain.pagamento.FormaPagamento;
import java.math.BigDecimal;

public record RegistrarPagamentoCommand(String ordemServicoId, BigDecimal valor, FormaPagamento formaPagamento) {}
