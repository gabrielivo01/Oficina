package io.github.gabrielivo.oficina.infrastructure.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/** Ajustes de observação (tracing): o que não vira span. */
@Configuration(proxyBeanMethods = false)
public class ObservabilidadeConfig {

    /**
     * Requisições ao actuator (probes do Kubernetes, scrape do Prometheus a cada poucos segundos) não geram trace: só
     * poluiriam o Jaeger. As métricas de negócio e HTTP continuam no {@code /actuator/prometheus}.
     */
    @Bean
    ObservationPredicate ignorarRequisicoesDoActuator() {
        return (nome, contexto) -> !(contexto instanceof ServerRequestObservationContext servidor
                && servidor.getCarrier() != null
                && servidor.getCarrier().getRequestURI().startsWith("/actuator"));
    }
}
