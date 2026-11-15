-- Etapa 2.2: novos status AGUARDANDO_PAGAMENTO e CANCELADA, histórico de status e índice da listagem paginada.

-- A check gerada pelo Hibernate só conhecia os 6 status antigos (ddl-auto=update nunca a atualizaria).
ALTER TABLE ordem_servico DROP CONSTRAINT IF EXISTS ordem_servico_status_check;
ALTER TABLE ordem_servico ADD CONSTRAINT ordem_servico_status_check CHECK (CASE status
    WHEN 'RECEBIDA' THEN TRUE WHEN 'EM_DIAGNOSTICO' THEN TRUE WHEN 'AGUARDANDO_APROVACAO' THEN TRUE
    WHEN 'AGUARDANDO_PAGAMENTO' THEN TRUE WHEN 'EM_EXECUCAO' THEN TRUE WHEN 'FINALIZADA' THEN TRUE
    WHEN 'ENTREGUE' THEN TRUE WHEN 'CANCELADA' THEN TRUE ELSE FALSE END);

CREATE TABLE historico_status_os (
    id               VARCHAR(36)  NOT NULL,
    ordem_servico_id VARCHAR(255) NOT NULL,
    sequencia        INTEGER      NOT NULL,
    status_anterior  VARCHAR(30),
    status_novo      VARCHAR(30)  NOT NULL,
    alterado_em      TIMESTAMP(6) NOT NULL,
    origem           VARCHAR(20)  NOT NULL,
    motivo           VARCHAR(255),
    CONSTRAINT historico_status_os_pkey PRIMARY KEY (id),
    CONSTRAINT fk_historico_status_os_ordem_servico FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id),
    CONSTRAINT uk_historico_status_os_sequencia UNIQUE (ordem_servico_id, sequencia),
    CONSTRAINT historico_status_os_origem_check CHECK (CASE origem
        WHEN 'USUARIO' THEN TRUE WHEN 'SAGA' THEN TRUE WHEN 'SISTEMA' THEN TRUE ELSE FALSE END)
);

-- OS que já existiam ganham uma linha inicial com o status atual (o histórico anterior não foi registrado).
-- O id reaproveita o da OS (UUID único, uma linha por OS) para não depender de função de UUID específica do banco.
INSERT INTO historico_status_os (id, ordem_servico_id, sequencia, status_anterior, status_novo, alterado_em, origem, motivo)
SELECT id, id, 1, NULL, status, COALESCE(status_desde, atualizado_em, criado_em, CURRENT_TIMESTAMP), 'SISTEMA',
       'Registro inicial na migração do histórico'
FROM ordem_servico;

CREATE INDEX idx_ordem_servico_status_criado_em ON ordem_servico (status, criado_em);
