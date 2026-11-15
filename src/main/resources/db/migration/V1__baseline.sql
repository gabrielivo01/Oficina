-- Baseline do schema do OS Service.
-- Gerado a partir do schema que o Hibernate (ddl-auto=update) criava no PostgreSQL 16 (pg_dump --schema-only),
-- com os mesmos nomes de constraint, para que bancos já existentes sejam marcados com baseline (V1) sem divergência.
-- SQL mantido portável (PostgreSQL e H2) porque os testes @DataJpaTest legados rodam em H2.
-- As checks de enum usam CASE em vez de IN (ou de uma cadeia de OR, que o H2 reescreve como IN): no H2 2.x, um
-- CHECK ... IN (constantes) guarda a sessão que o criou (a do Flyway) e, quando ela é fechada, toda inserção falha com
-- "database has been closed". A semântica no PostgreSQL é a mesma da check gerada pelo Hibernate.

CREATE TABLE endereco (
    id            VARCHAR(36)  NOT NULL,
    atualizado_em TIMESTAMP(6),
    bairro        VARCHAR(100),
    cep           VARCHAR(8),
    cidade        VARCHAR(100),
    complemento   VARCHAR(100),
    criado_em     TIMESTAMP(6),
    logradouro    VARCHAR(150),
    numero        VARCHAR(20),
    uf            VARCHAR(2),
    CONSTRAINT endereco_pkey PRIMARY KEY (id)
);

CREATE TABLE cliente (
    id            VARCHAR(36)  NOT NULL,
    atualizado_em TIMESTAMP(6),
    cpf           VARCHAR(11)  NOT NULL,
    criado_em     TIMESTAMP(6),
    nome          VARCHAR(150) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    telefone      VARCHAR(20),
    endereco_id   VARCHAR(36),
    CONSTRAINT cliente_pkey PRIMARY KEY (id),
    CONSTRAINT ukr1u8010d60num5vc8fp0q1j2a UNIQUE (cpf),
    CONSTRAINT cliente_status_check CHECK (CASE status WHEN 'ATIVO' THEN TRUE WHEN 'INATIVO' THEN TRUE ELSE FALSE END)
);

CREATE TABLE veiculo (
    id            VARCHAR(36)  NOT NULL,
    ano           INTEGER,
    atualizado_em TIMESTAMP(6),
    criado_em     TIMESTAMP(6),
    marca         VARCHAR(100) NOT NULL,
    modelo        VARCHAR(100) NOT NULL,
    placa         VARCHAR(10)  NOT NULL,
    cliente_id    VARCHAR(36)  NOT NULL,
    CONSTRAINT veiculo_pkey PRIMARY KEY (id),
    CONSTRAINT ukluoyk9d8idgi0wif7bxtefsr5 UNIQUE (placa)
);

CREATE TABLE peca (
    id                 VARCHAR(36)  NOT NULL,
    atualizado_em      TIMESTAMP(6),
    criado_em          TIMESTAMP(6),
    descricao          TEXT,
    estoque_minimo     INTEGER      NOT NULL,
    nome               VARCHAR(150) NOT NULL,
    preco              NUMERIC(10, 2),
    quantidade_estoque INTEGER      NOT NULL,
    CONSTRAINT peca_pkey PRIMARY KEY (id)
);

CREATE TABLE ordem_servico (
    id            VARCHAR(255) NOT NULL,
    atualizado_em TIMESTAMP(6),
    criado_em     TIMESTAMP(6),
    status        VARCHAR(30)  NOT NULL,
    status_desde  TIMESTAMP(6),
    valor_total   NUMERIC(10, 2),
    cliente_id    VARCHAR(36)  NOT NULL,
    veiculo_id    VARCHAR(36)  NOT NULL,
    CONSTRAINT ordem_servico_pkey PRIMARY KEY (id),
    CONSTRAINT ordem_servico_status_check CHECK (CASE status
        WHEN 'RECEBIDA' THEN TRUE WHEN 'EM_DIAGNOSTICO' THEN TRUE WHEN 'AGUARDANDO_APROVACAO' THEN TRUE
        WHEN 'EM_EXECUCAO' THEN TRUE WHEN 'FINALIZADA' THEN TRUE WHEN 'ENTREGUE' THEN TRUE ELSE FALSE END)
);

CREATE TABLE item_ordem_servico (
    id               VARCHAR(255)   NOT NULL,
    descricao        VARCHAR(150)   NOT NULL,
    quantidade       INTEGER,
    tipo             VARCHAR(10)    NOT NULL,
    valor            NUMERIC(10, 2) NOT NULL,
    ordem_servico_id VARCHAR(255)   NOT NULL,
    peca_id          VARCHAR(36),
    CONSTRAINT item_ordem_servico_pkey PRIMARY KEY (id),
    CONSTRAINT item_ordem_servico_tipo_check CHECK (CASE tipo WHEN 'SERVICO' THEN TRUE WHEN 'PECA' THEN TRUE ELSE FALSE END)
);

CREATE TABLE item_peca (
    id               VARCHAR(36)  NOT NULL,
    quantidade       INTEGER      NOT NULL,
    valor            NUMERIC(10, 2),
    ordem_servico_id VARCHAR(255) NOT NULL,
    peca_id          VARCHAR(36)  NOT NULL,
    CONSTRAINT item_peca_pkey PRIMARY KEY (id)
);

CREATE TABLE pagamento (
    id               VARCHAR(36)    NOT NULL,
    criado_em        TIMESTAMP(6),
    forma_pagamento  VARCHAR(30),
    valor            NUMERIC(10, 2) NOT NULL,
    ordem_servico_id VARCHAR(255)   NOT NULL,
    CONSTRAINT pagamento_pkey PRIMARY KEY (id),
    CONSTRAINT pagamento_forma_pagamento_check CHECK (forma_pagamento IS NULL OR CASE forma_pagamento
        WHEN 'PIX' THEN TRUE WHEN 'DINHEIRO' THEN TRUE WHEN 'CARTAO' THEN TRUE ELSE FALSE END)
);

CREATE TABLE usuario (
    id        VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP(6),
    login     VARCHAR(100) NOT NULL,
    senha     VARCHAR(255) NOT NULL,
    CONSTRAINT usuario_pkey PRIMARY KEY (id),
    CONSTRAINT ukpm3f4m4fqv89oeeeac4tbe2f4 UNIQUE (login)
);

ALTER TABLE cliente
    ADD CONSTRAINT fk64nr9yt889by5lufr1boo5i4s FOREIGN KEY (endereco_id) REFERENCES endereco (id);
ALTER TABLE veiculo
    ADD CONSTRAINT fkny7f0cx7lnl40poaqcijjxyao FOREIGN KEY (cliente_id) REFERENCES cliente (id);
ALTER TABLE ordem_servico
    ADD CONSTRAINT fkbgbo9jhvq1cuycsv5tvynn1ig FOREIGN KEY (cliente_id) REFERENCES cliente (id);
ALTER TABLE ordem_servico
    ADD CONSTRAINT fkny45eyvfgf7oq36vfh3nsa6qe FOREIGN KEY (veiculo_id) REFERENCES veiculo (id);
ALTER TABLE item_ordem_servico
    ADD CONSTRAINT fkgv3sbcw8fq128il6x69tjkktc FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id);
ALTER TABLE item_ordem_servico
    ADD CONSTRAINT fknmly75ucrbn4k8hvhk019j18r FOREIGN KEY (peca_id) REFERENCES peca (id);
ALTER TABLE item_peca
    ADD CONSTRAINT fkd18jglyu9531v20n0pyx87hdu FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id);
ALTER TABLE item_peca
    ADD CONSTRAINT fkno86p05fr2grs895xynthpq0e FOREIGN KEY (peca_id) REFERENCES peca (id);
ALTER TABLE pagamento
    ADD CONSTRAINT fkm23c4jnmyicumjhpg3muw4gsd FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id);
