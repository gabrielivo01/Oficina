-- Etapa 2.4: Transactional Outbox e Inbox (ADR 0009).

CREATE TABLE outbox (
    id          VARCHAR(36)  NOT NULL,
    tipo        VARCHAR(100) NOT NULL,
    exchange    VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    criado_em   TIMESTAMP(6) NOT NULL,
    enviado_em  TIMESTAMP(6),
    tentativas  INTEGER      DEFAULT 0 NOT NULL,
    ultimo_erro VARCHAR(500),
    CONSTRAINT outbox_pkey PRIMARY KEY (id)
);

-- Busca dos pendentes (enviado_em nulo) em ordem de gravação.
CREATE INDEX idx_outbox_pendentes ON outbox (enviado_em, criado_em);

CREATE TABLE inbox (
    message_id    VARCHAR(64)  NOT NULL,
    tipo          VARCHAR(100) NOT NULL,
    processado_em TIMESTAMP(6) NOT NULL,
    CONSTRAINT inbox_pkey PRIMARY KEY (message_id)
);
