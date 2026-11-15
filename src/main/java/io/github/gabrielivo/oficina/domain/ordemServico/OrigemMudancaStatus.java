package io.github.gabrielivo.oficina.domain.ordemServico;

/** Quem provocou uma mudança de status da OS (registrado em {@link HistoricoStatusOrdemServico}). */
public enum OrigemMudancaStatus {
    /** Ação de uma pessoa via API (atendente, mecânico, cliente). */
    USUARIO,
    /** Orquestrador da Saga, ao reagir a um evento de outro serviço. */
    SAGA,
    /** Processo automático ou integração técnica (jobs, integrações externas). */
    SISTEMA
}
