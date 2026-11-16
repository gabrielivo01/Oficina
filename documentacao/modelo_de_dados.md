# Modelo de Dados

O schema é versionado pelo **Flyway** em `src/main/resources/db/migration` (o Hibernate só valida,
`ddl-auto=validate`). `V1__baseline.sql` reproduz o schema que o Hibernate criava no PostgreSQL até a Fase 3,
com os mesmos nomes de constraint; bancos que já existiam recebem baseline na V1 sem reexecutá-la. Toda mudança
de tabela/coluna é uma migration nova e é refletida neste documento.

```dbml
Table usuario {
  id varchar(36) [primary key] // UUID.toString()
  login varchar(100) [unique, not null]
  senha text [not null]
  criado_em timestamp
}

Table cliente {
  id varchar(36) [primary key] // UUID.toString()
  cpf char(11) [unique, not null]
  nome varchar(150) [not null]
  telefone varchar(20)
  endereco_id varchar(36) [ref: > endereco.id]
  status varchar(20) [not null, default: 'ATIVO'] // ATIVO, INATIVO — consultado pelo lambda de autenticação por CPF
  criado_em timestamp
  atualizado_em timestamp
}

Table endereco {
  id varchar(36) [primary key] // UUID.toString()
  cep char(8)
  logradouro varchar(150)
  numero varchar(20)
  complemento varchar(100)
  bairro varchar(100)
  cidade varchar(100)
  uf char(2)
  criado_em timestamp
  atualizado_em timestamp
}

Table veiculo {
  id varchar(36) [primary key] // UUID.toString()
  cliente_id varchar(36) [not null, ref: > cliente.id]
  placa varchar(10) [unique, not null]
  marca varchar(100) [not null]
  modelo varchar(100) [not null]
  ano integer
  criado_em timestamp
  atualizado_em timestamp
}

Table peca {
  id varchar(36) [primary key] // UUID.toString()
  nome varchar(150) [not null]
  descricao text
  quantidade_estoque integer [not null, default: 0]
  estoque_minimo integer [not null, default: 0]
  preco decimal(10,2)
  criado_em timestamp
  atualizado_em timestamp
}

Table ordem_servico {
  id varchar(36) [primary key] // UUID.toString()
  cliente_id varchar(36) [not null, ref: > cliente.id]
  veiculo_id varchar(36) [not null, ref: > veiculo.id]
  status varchar(30) [not null] // RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, AGUARDANDO_PAGAMENTO, EM_EXECUCAO, FINALIZADA, ENTREGUE, CANCELADA (V2)
  status_desde timestamp // atualizado só em transição de status; base da métrica de tempo médio por status
  valor_total decimal(10,2)
  criado_em timestamp
  atualizado_em timestamp

  indexes {
    (status, criado_em) [name: 'idx_ordem_servico_status_criado_em'] // listagem paginada com filtro de status (V2)
  }
}

Table historico_status_os { // V2: uma linha por mudança de status (e uma na abertura)
  id varchar(36) [primary key] // UUID.toString()
  ordem_servico_id varchar(36) [not null, ref: > ordem_servico.id]
  sequencia integer [not null] // 1 = abertura; único por OS (desempata transições no mesmo instante)
  status_anterior varchar(30) // nulo na abertura
  status_novo varchar(30) [not null]
  alterado_em timestamp [not null]
  origem varchar(20) [not null] // USUARIO, SAGA, SISTEMA
  motivo varchar(255)

  indexes {
    (ordem_servico_id, sequencia) [unique]
  }
}

Table outbox { // V3: Transactional Outbox (ADR 0009); gravado na transação da mudança de estado
  id varchar(36) [primary key] // = messageId do envelope
  tipo varchar(100) [not null] // ex.: StatusOrdemServicoAlterado
  exchange varchar(100) [not null]
  routing_key varchar(100) [not null]
  payload text [not null] // envelope JSON completo, publicado como está
  criado_em timestamp [not null]
  enviado_em timestamp // nulo = pendente; preenchido só após o publisher confirm
  tentativas integer [not null, default: 0]
  ultimo_erro varchar(500)

  indexes {
    (enviado_em, criado_em) [name: 'idx_outbox_pendentes']
  }
}

Table inbox { // V3: idempotência dos consumidores (ADR 0009)
  message_id varchar(64) [primary key] // messageId da mensagem processada
  tipo varchar(100) [not null]
  processado_em timestamp [not null]
}

Table item_ordem_servico {
  id varchar(36) [primary key] // UUID.toString()
  ordem_servico_id varchar(36) [not null, ref: > ordem_servico.id]
  peca_id varchar(36) [ref: > peca.id]
  descricao varchar(150) [not null]
  tipo varchar(10) [not null] // SERVICO, PECA
  valor decimal(10,2) [not null]
  quantidade integer
}

Table pagamento {
  id varchar(36) [primary key] // UUID.toString()
  ordem_servico_id varchar(36) [not null, ref: > ordem_servico.id]
  valor decimal(10,2) [not null]
  forma_pagamento varchar(30) // PIX, DINHEIRO, CARTAO
  criado_em timestamp
}
```

O bloco acima está em [DBML](https://dbml.dbdiagram.io/) — cole em
dbdiagram.io para uma visualização interativa. O diagrama abaixo (Mermaid)
renderiza direto no GitHub/GitLab, sem ferramenta externa:

```mermaid
erDiagram
    USUARIO {
        varchar id PK
        varchar login UK
        text senha
        timestamp criado_em
    }
    CLIENTE {
        varchar id PK
        char cpf UK
        varchar nome
        varchar telefone
        varchar endereco_id FK
        varchar status
        timestamp criado_em
        timestamp atualizado_em
    }
    ENDERECO {
        varchar id PK
        char cep
        varchar logradouro
        varchar numero
        varchar complemento
        varchar bairro
        varchar cidade
        char uf
    }
    VEICULO {
        varchar id PK
        varchar cliente_id FK
        varchar placa UK
        varchar marca
        varchar modelo
        integer ano
    }
    PECA {
        varchar id PK
        varchar nome
        text descricao
        integer quantidade_estoque
        integer estoque_minimo
        decimal preco
    }
    ORDEM_SERVICO {
        varchar id PK
        varchar cliente_id FK
        varchar veiculo_id FK
        varchar status
        timestamp status_desde
        decimal valor_total
    }
    HISTORICO_STATUS_OS {
        varchar id PK
        varchar ordem_servico_id FK
        integer sequencia
        varchar status_anterior
        varchar status_novo
        timestamp alterado_em
        varchar origem
        varchar motivo
    }
    ITEM_ORDEM_SERVICO {
        varchar id PK
        varchar ordem_servico_id FK
        varchar peca_id FK
        varchar descricao
        varchar tipo
        decimal valor
        integer quantidade
    }
    PAGAMENTO {
        varchar id PK
        varchar ordem_servico_id FK
        decimal valor
        varchar forma_pagamento
    }

    CLIENTE ||--o| ENDERECO : "mora em"
    CLIENTE ||--o{ VEICULO : "possui"
    CLIENTE ||--o{ ORDEM_SERVICO : "solicita"
    VEICULO ||--o{ ORDEM_SERVICO : "é atendido em"
    ORDEM_SERVICO ||--o{ ITEM_ORDEM_SERVICO : "contém"
    ORDEM_SERVICO ||--|{ HISTORICO_STATUS_OS : "registra mudanças de status"
    PECA ||--o{ ITEM_ORDEM_SERVICO : "referenciada por (tipo=PECA)"
    ORDEM_SERVICO ||--o| PAGAMENTO : "é paga por"
```

## Justificativa da escolha do banco (PostgreSQL/RDS)

Ver `documentacao/rfcs/0002-escolha-do-banco-gerenciado.md` para a justificativa
formal completa (tipos de dados usados no schema, integridade referencial sob
concorrência, custo/maturidade no RDS, alternativas descartadas). Resumo: o
modelo é fortemente relacional — abrir uma OS com item do tipo `PECA` decrementa
estoque na mesma transação, pagamento é 1:1 com OS, e há múltiplas constraints
`UNIQUE`/`NOT NULL` que dependem de integridade referencial real — PostgreSQL
atende isso nativamente sem desnormalização.

## Relacionamentos entre os módulos

- **`cliente` ↔ `endereco`** (1:0..1): um cliente pode não ter endereço
  cadastrado (`endereco_id` é opcional); quando existe, é gerenciado com
  `CascadeType.ALL` a partir de `Cliente` (não existe `EnderecoRepository`
  independente sendo usado fora desse relacionamento).
- **`cliente` ↔ `veiculo`** (1:N): um cliente pode ter vários veículos;
  `Veiculo.cliente_id` é `NOT NULL` — não existe veículo "órfão".
- **`cliente`/`veiculo` ↔ `ordem_servico`** (1:N cada): toda OS pertence a
  exatamente um cliente e um veículo, ambos obrigatórios na criação
  (`OrdemServicoService.abrir` busca os dois antes de instanciar a OS).
- **`ordem_servico` ↔ `item_ordem_servico`** (1:N): itens são o único jeito de
  compor o `valor_total` da OS (`OrdemServico.recalcularTotal`); um item do
  tipo `SERVICO` não referencia peça, um item do tipo `PECA` referencia
  exatamente uma (`ItemOrdemServico`/`domain/ordemServico/AdicionarItemCommand`
  validam essa regra).
- **`ordem_servico` ↔ `historico_status_os`** (1:N, ao menos 1): gravado em cascata pelo agregado
  `OrdemServico` (todo `alterarStatus` adiciona uma linha); nunca alterado depois de gravado. OS anteriores à
  V2 receberam uma linha inicial com o status vigente (origem `SISTEMA`).
- **`outbox` e `inbox`**: tabelas técnicas, sem chave estrangeira para o modelo. Uma linha do outbox nasce na
  mesma transação da mudança de estado (hoje: cada transição de status da OS gera um
  `StatusOrdemServicoAlterado`) e é publicada depois do commit; o inbox guarda o `messageId` de cada mensagem
  consumida, na mesma transação do processamento. Limpeza periódica das linhas antigas: pendente (ADR 0009).
- **`peca` ↔ `item_ordem_servico`** (1:N, opcional): só itens do tipo `PECA`
  têm `peca_id` preenchido; adicionar um item desses decrementa
  `peca.quantidade_estoque` (`PecaService`/`OrdemServicoService`), removê-lo
  ou cancelar a OS repõe o estoque.
- **`ordem_servico` ↔ `pagamento`** (1:0..1): pagamento só é permitido quando
  a OS está `FINALIZADA` ou `ENTREGUE` (regra em `OrdemServicoService`), e uma
  OS tem no máximo um pagamento associado.
- **`usuario`**: isolado do restante do modelo — não referencia nem é
  referenciado por `cliente`/`ordem_servico`/etc. Representa login de
  staff/admin (ver `domain/usuario/`), um modelo de credencial
  completamente separado do de `cliente` (ver
  `documentacao/rfcs/0003-estrategia-de-autenticacao.md`).
