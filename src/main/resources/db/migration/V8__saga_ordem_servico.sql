CREATE TABLE saga_ordem_servico (
    os_id                  VARCHAR(255) NOT NULL,
    etapa_atual            VARCHAR(50)  NOT NULL,
    situacao               VARCHAR(30)  NOT NULL,
    motivo_compensacao     VARCHAR(40),
    reserva_id             VARCHAR(36),
    diagnostico_solicitado BOOLEAN     NOT NULL DEFAULT FALSE,
    orcamento_id           VARCHAR(36),
    valor_orcamento        NUMERIC(10, 2),
    cobranca_id            VARCHAR(36),
    pagamento_id_externo   VARCHAR(200),
    reserva_liberada       BOOLEAN     NOT NULL DEFAULT FALSE,
    prazo_expiracao        TIMESTAMP(6) WITH TIME ZONE,
    tentativas             INTEGER     NOT NULL DEFAULT 0,
    falha_compensacao      VARCHAR(1000),
    criado_em              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    atualizado_em          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    versao                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT saga_ordem_servico_pkey PRIMARY KEY (os_id),
    CONSTRAINT fk_saga_ordem_servico_os FOREIGN KEY (os_id) REFERENCES ordem_servico (id),
    CONSTRAINT saga_ordem_servico_situacao_check CHECK (CASE situacao
        WHEN 'EM_ANDAMENTO' THEN TRUE
        WHEN 'COMPENSANDO' THEN TRUE
        WHEN 'CONCLUIDA' THEN TRUE
        WHEN 'COMPENSADA' THEN TRUE
        WHEN 'FALHA_COMPENSACAO' THEN TRUE
        ELSE FALSE END)
);

CREATE TABLE historico_eventos_saga (
    saga_id     VARCHAR(255) NOT NULL,
    sequencia   INTEGER      NOT NULL,
    message_id  VARCHAR(64),
    tipo        VARCHAR(100) NOT NULL,
    ocorrido_em TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    aplicado    BOOLEAN      NOT NULL,
    detalhe     VARCHAR(255),
    CONSTRAINT historico_eventos_saga_pkey PRIMARY KEY (saga_id, sequencia),
    CONSTRAINT fk_historico_eventos_saga FOREIGN KEY (saga_id) REFERENCES saga_ordem_servico (os_id)
);

CREATE INDEX idx_saga_ordem_servico_expiracao
    ON saga_ordem_servico (situacao, prazo_expiracao);

-- Aberturas antigas ainda sem progresso podem ser encaminhadas pelo job.
INSERT INTO saga_ordem_servico (
    os_id, etapa_atual, situacao, diagnostico_solicitado, reserva_liberada,
    tentativas, criado_em, atualizado_em, versao
)
SELECT id, 'AGUARDANDO_DIAGNOSTICO_INICIADO', 'EM_ANDAMENTO', FALSE, FALSE,
       0, COALESCE(criado_em, CURRENT_TIMESTAMP), COALESCE(atualizado_em, criado_em, CURRENT_TIMESTAMP), 0
FROM ordem_servico
WHERE status = 'RECEBIDA';

-- Fluxos antigos avançados manualmente não têm referências confiáveis aos participantes externos.
-- Deixá-los explícitos para reconciliação é mais seguro do que emitir compensações sem IDs correlacionados.
INSERT INTO saga_ordem_servico (
    os_id, etapa_atual, situacao, diagnostico_solicitado, reserva_liberada,
    tentativas, falha_compensacao, criado_em, atualizado_em, versao
)
SELECT id, 'AGUARDANDO_DIAGNOSTICO_INICIADO', 'FALHA_COMPENSACAO', TRUE, FALSE,
       0, 'OS preexistente sem correlação completa com Execution/Billing; reconciliação manual necessária.',
       COALESCE(criado_em, CURRENT_TIMESTAMP), COALESCE(atualizado_em, criado_em, CURRENT_TIMESTAMP), 0
FROM ordem_servico
WHERE status IN ('EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'AGUARDANDO_PAGAMENTO', 'EM_EXECUCAO');

INSERT INTO saga_ordem_servico (
    os_id, etapa_atual, situacao, diagnostico_solicitado, reserva_liberada,
    tentativas, criado_em, atualizado_em, versao
)
SELECT id, 'AGUARDANDO_FINALIZACAO_EXECUCAO', 'CONCLUIDA', TRUE, FALSE,
       0, COALESCE(criado_em, CURRENT_TIMESTAMP), COALESCE(atualizado_em, criado_em, CURRENT_TIMESTAMP), 0
FROM ordem_servico
WHERE status = 'FINALIZADA';

INSERT INTO saga_ordem_servico (
    os_id, etapa_atual, situacao, diagnostico_solicitado, reserva_liberada,
    tentativas, criado_em, atualizado_em, versao
)
SELECT id, 'AGUARDANDO_FINALIZACAO_EXECUCAO', 'CONCLUIDA', TRUE, FALSE,
       0, COALESCE(criado_em, CURRENT_TIMESTAMP), COALESCE(atualizado_em, criado_em, CURRENT_TIMESTAMP), 0
FROM ordem_servico
WHERE status = 'ENTREGUE';

INSERT INTO saga_ordem_servico (
    os_id, etapa_atual, situacao, diagnostico_solicitado, reserva_liberada,
    tentativas, criado_em, atualizado_em, versao
)
SELECT id, 'COMPENSANDO_RESERVA', 'COMPENSADA', TRUE, TRUE,
       0, COALESCE(criado_em, CURRENT_TIMESTAMP), COALESCE(atualizado_em, criado_em, CURRENT_TIMESTAMP), 0
FROM ordem_servico
WHERE status = 'CANCELADA';
