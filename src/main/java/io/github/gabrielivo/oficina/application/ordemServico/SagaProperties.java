package io.github.gabrielivo.oficina.application.ordemServico;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.saga")
public record SagaProperties(
        @DefaultValue("30m") Duration prazoPagamento,
        @DefaultValue("30s") Duration prazoCompensacao,
        @DefaultValue("30s") Duration intervaloVerificacao,
        @DefaultValue("3") int maxTentativasCompensacao) {

    public SagaProperties {
        if (prazoPagamento == null
                || prazoPagamento.isNegative()
                || prazoPagamento.isZero()
                || prazoCompensacao == null
                || prazoCompensacao.isNegative()
                || prazoCompensacao.isZero()
                || intervaloVerificacao == null
                || intervaloVerificacao.isNegative()
                || intervaloVerificacao.isZero()
                || maxTentativasCompensacao < 1) {
            throw new IllegalArgumentException("Os prazos e tentativas configurados para a Saga devem ser positivos.");
        }
    }
}
