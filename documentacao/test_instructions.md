# 📘 Documentação de Execução e Testes Locais

## 🧩 Visão Geral

Este documento descreve os passos necessários para configurar o ambiente local, executar a aplicação e realizar testes básicos de CRUD, utilizando como exemplo o domínio **Cliente**. As instruções podem ser adaptadas para outros domínios do sistema.

---

# ⚙️ Configuração do Ambiente Local

Antes de iniciar a aplicação, é necessário realizar algumas configurações:

## 🔐 1. Desabilitar autenticação (JWT)

Para facilitar os testes locais, desabilite a validação de segurança JWT:

No arquivo `application.properties`, configure:

```properties
app.security.enabled=false
```

> ⚠️ **Importante:** Essa configuração deve ser usada apenas em ambiente local/desenvolvimento.

---

## 📦 2. Instalar dependências

Execute o comando abaixo para baixar e instalar todas as dependências do projeto:

```bash
./mvnw clean install
```

---

## ▶️ 3. Executar a aplicação

Inicie a aplicação em modo desenvolvimento com:

```bash
./mvnw clean quarkus:dev
```

Esse modo permite:

* Hot reload (atualizações em tempo real)
* Logs detalhados para debug

---

## 🌐 4. Acessar a documentação da API (Swagger)

Após subir a aplicação, acesse:

```
http://localhost:8080/swagger-ui.html
```

Através dessa interface, é possível:

* Visualizar todos os endpoints disponíveis
* Executar requisições diretamente pelo navegador
* Testar fluxos completos da API

---

# 🧪 Testes Funcionais (Exemplo: Domínio Cliente)

A seguir, um fluxo completo para testar operações CRUD no domínio **Cliente**.

---

## ➕ 1. Criar Cliente (Create)

### 📌 Endpoint esperado:

`POST /clientes`

### 📥 Payload de exemplo:

```json
{
  "cpf": "05265162160",
  "nome": "Gabriel",
  "telefone": "61992878776",
  "endereco": {
    "cep": "12345678",
    "logradouro": "Rua das Flores",
    "numero": "100",
    "complemento": "Apto 101",
    "bairro": "Centro",
    "cidade": "Brasília",
    "uf": "DF"
  }
}
```

### ⚠️ Regras importantes:

* O CPF deve ser **válido** (há validação no backend)
* Campos obrigatórios devem ser preenchidos

### 📤 Resposta esperada:

* Retorno com status `201 Created`
* Corpo da resposta contendo o **ID do cliente gerado**

---

## 🔍 2. Consultar Cliente (Read)

### 📌 Endpoint:

`GET /clientes/{id}`

### 📥 Entrada:

* ID retornado na criação

### 📤 Resultado esperado:

* Dados completos do cliente

---

## ✏️ 3. Atualizar Cliente (Update)

### 📌 Endpoint:

`PUT /clientes/{id}`

### 📥 Entrada:

* ID do cliente
* Payload atualizado

### 📤 Resultado esperado:

* Cliente atualizado com sucesso

---

## ❌ 4. Remover Cliente (Delete)

### 📌 Endpoint:

`DELETE /clientes/{id}`

### 📤 Resultado esperado:

* Remoção lógica ou física (dependendo da implementação)
* Status `204 No Content` ou similar

---

# 🔁 Reutilização do Fluxo

O fluxo acima pode ser replicado para outros domínios do sistema, respeitando:

* Estrutura específica de cada entidade
* Validações de negócio
* Relacionamentos entre entidades

---

# 🧠 Boas Práticas para Testes

* Sempre valide os dados antes de enviar (ex: CPF válido)
* Utilize ferramentas como:

  * Swagger (já disponível)
  * Postman / Insomnia (opcional)
* Teste cenários de erro:

  * CPF inválido
  * Campos obrigatórios ausentes
  * ID inexistente

---

# 🚀 Conclusão

Com esses passos, é possível:

* Rodar o sistema localmente
* Testar endpoints de forma rápida
* Validar regras de negócio antes de deploy

Se necessário, este guia pode ser expandido com:

* Testes automatizados
* Cenários de integração
* Fluxos de outros domínios

---
