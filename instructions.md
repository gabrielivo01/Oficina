# 🛠️ Oficina Mecânica - Backend (Guia para IA)

## 📌 Objetivo

Este documento descreve o contexto, domínio, regras e estrutura do sistema de backend para uma oficina mecânica.
Ele deve ser utilizado como base para geração de código por ferramentas de IA.

---

# 🧱 Arquitetura

O sistema segue:

* Monólito modular
* Arquitetura multicamadas
* Domain-Driven Design (DDD)

## Camadas:

```
domain/         -> regras de negócio
application/    -> casos de uso
infrastructure/ -> banco, frameworks
presentation/   -> controllers REST
```

---

# 📦 Organização de pacotes

```
domain/
  cliente/
  veiculo/
  ordemservico/

application/
infrastructure/
shared/
```

---

# 🧠 Regras de DDD

* Cada domínio deve ser isolado
* Não acessar diretamente outro domínio sem mediação
* Entidades devem conter regras de negócio
* Services devem representar regras complexas
* Repositories são interfaces no domínio

---

# 📊 Domínios

## 👤 Cliente

Representa o dono do veículo.

### Regras:

* CPF deve ser único
* Nome obrigatório

---

## 🚗 Veículo

Representa um veículo pertencente a um cliente.

### Atributos:

* id
* placa (única)
* modelo
* marca
* ano
* cliente_id 

### Regras:

* Um veículo pertence a um cliente
* Placa deve ser única

---

## 🔧 Ordem de Serviço

Representa um serviço executado no veículo.

### Regras:

* Uma OS pertence a um veículo
* Status deve seguir fluxo válido
* Não pode finalizar sem itens
* Tem os seguintes status:  
-   Recebida;
-   Em diagnóstico;
-   Aguardando aprovação;
-   Em execução;
-   Finalizada;
-   Entregue.

---

## 🧩 Itens da Ordem de Serviço

Representa serviços ou peças.


## 🧰 Peça

Representa um item físico que pode ser utilizado em serviços da oficina (ex: filtro de óleo, pastilha de freio).

### Atributos:

- id
- nome
- descricao
- preco
- quantidade_estoque

### Regras:

- Nome é obrigatório
- Preço deve ser maior que zero
- Quantidade em estoque não pode ser negativa

---

## 🧩 Item da Ordem de Serviço (ItemOS)

Representa um item vinculado a uma ordem de serviço, podendo ser um serviço ou uma peça utilizada.

### Atributos:

- id
- ordem_servico_id
- descricao
- tipo (SERVICO ou PECA)
- valor
- quantidade (quando for peça)
- peca_id (opcional)

### Regras:

- Deve estar vinculado a uma ordem de serviço
- Se tipo = PECA:
  - Deve referenciar uma peça existente
  - Deve ter quantidade maior que zero
- Se tipo = SERVICO:
  - Não deve ter referência para peça
- Valor deve ser maior ou igual a zero






# 🗄️ Banco de dados

Banco relacional: PostgreSQL

### Modelo de dados:

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

Table ordem_servico {
  id varchar(36) [primary key] // UUID.toString()
  cliente_id varchar(36) [not null, ref: > cliente.id]
  veiculo_id varchar(36) [not null, ref: > veiculo.id]
  status varchar(30) [not null] // ABERTA, EM_ANDAMENTO, FINALIZADA
  valor_total decimal(10,2)
  criado_em timestamp
  atualizado_em timestamp
}

Table diagnostico {
  id varchar(36) [primary key] // UUID.toString()
  ordem_servico_id varchar(36) [not null, ref: > ordem_servico.id]
  descricao text [not null]
  criado_em timestamp
}

Table item_servico {
  id varchar(36) [primary key] // UUID.toString()
  ordem_servico_id varchar(36) [not null, ref: > ordem_servico.id]
  descricao varchar(150) [not null]
  valor decimal(10,2)
}

Table peca {
  id varchar(36) [primary key] // UUID.toString()
  nome varchar(150) [not null]
  quantidade_estoque integer [not null, default: 0]
  preco decimal(10,2)
  criado_em timestamp
  atualizado_em timestamp
}

Table item_peca {
  id varchar(36) [primary key] // UUID.toString()
  ordem_servico_id varchar(36) [not null, ref: > ordem_servico.id]
  peca_id varchar(36) [not null, ref: > peca.id]
  quantidade integer [not null]
  valor decimal(10,2)
}

Table pagamento {
  id varchar(36) [primary key] // UUID.toString()
  ordem_servico_id varchar(36) [not null, ref: > ordem_servico.id]
  valor decimal(10,2) [not null]
  forma_pagamento varchar(30) // PIX, DINHEIRO, CARTAO
  criado_em timestamp
}




# ⚙️ Padrões de desenvolvimento

## Entidades

* Usar @Entity
* Usar encapsulamento (evitar setters públicos indiscriminados)

## Repositórios

* Interface
* Estender JpaRepository

## Services

* Conter lógica de negócio
* Não colocar regra em controller

## Controllers

* Apenas orquestração
* Validar entrada
* Retornar DTOs

---

# 🔄 Fluxos principais

## CRUD cliente

1. Recebe dados
2. Valida CPF único
3. Persiste/Deleta/atualiza cliente

---

## CRUD veículo

1. Verifica cliente existente
2. Valida placa única
3. Persiste/Deleta/atualiza veículo

---

## Abrir ordem de serviço

1. Verifica veículo
2. Cria OS com status ABERTA

---

## Adicionar item na OS

1. Verifica OS aberta
2. Adiciona item

---

## Finalizar OS

1. Verifica se possui itens
2. Atualiza status para FINALIZADA
3. Define data de fechamento

---


# 🚨 Regras importantes

* Nunca acessar banco diretamente fora de repository
* Nunca colocar regra de negócio em controller
* Sempre validar dados de entrada
* Separar entidade de DTO

---

# 🧪 Testes (futuro)

* Testes unitários para services
* Testes de integração para controllers

---

# 🎯 Objetivo da IA

A IA deve:

* Gerar código limpo e organizado
* Respeitar DDD
* Evitar lógica em controller
* Criar classes coesas
* Seguir estrutura de pacotes definida

---

# 📌 Observações finais

* Código deve ser simples e legível
* Evitar overengineering
* Priorizar clareza sobre complexidade
* Seguir boas práticas do Spring Boot

---
