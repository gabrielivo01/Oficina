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
  status varchar(30) [not null] // RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, EM_EXECUCAO, FINALIZADA, ENTREGUE
  valor_total decimal(10,2)
  criado_em timestamp
  atualizado_em timestamp
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


