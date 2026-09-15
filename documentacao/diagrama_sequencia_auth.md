# Diagrama de Sequência — Autenticação por CPF

Fluxo completo desde a chamada do cliente até o uso do token emitido em uma
rota protegida da aplicação principal. Ver o README do repositório
`oficina-auth-lambda` para o detalhamento do Lambda, e
`src/main/java/.../infrastructure/security/` neste repositório para o lado da
aplicação (`JwtAuthFilter`, `ClienteUserDetailsService`).

```mermaid
sequenceDiagram
    actor Cliente
    participant GW as API Gateway
    participant Lambda as Lambda (AuthCpfHandler)
    participant App as oficina-app
    participant DB as PostgreSQL

    Cliente ->> GW: POST /auth/cpf {"cpf": "..."}
    GW ->> Lambda: invoke (AWS_PROXY)
    Lambda ->> Lambda: CpfValidator.isValid(cpf)

    alt CPF com formato inválido
        Lambda -->> GW: 400 {"erro": "CPF inválido."}
        GW -->> Cliente: 400
    else CPF válido
        Lambda ->> App: GET /internal/clientes/{cpf}/status\nheader X-Internal-Api-Key
        App ->> DB: SELECT ... FROM cliente WHERE cpf = ?
        DB -->> App: cliente (ou nenhum resultado)
        App -->> Lambda: 200 {"existe": bool, "ativo": bool}

        alt cliente inexistente ou inativo
            Lambda -->> GW: 401 {"erro": "Cliente não encontrado ou inativo."}
            GW -->> Cliente: 401
        else cliente existe e está ativo
            Lambda ->> Lambda: JwtIssuer.emitirParaCliente(cpf)\n(sub=cpf, claim tipo=CLIENTE, HS256)
            Lambda -->> GW: 200 {"token": "..."}
            GW -->> Cliente: 200 {"token": "..."}
        end
    end

    Note over Cliente,App: Token em mãos, cliente chama uma rota protegida

    Cliente ->> GW: GET /ordens-servico\nAuthorization: Bearer <token>
    GW ->> App: proxy (HTTP_PROXY)
    App ->> App: JwtAuthFilter.doFilterInternal\nextrairTipo(token) == CLIENTE
    App ->> App: ClienteUserDetailsService.loadUserByUsername(cpf)\n(exige isAtivo())
    App -->> GW: 200 [...]
    GW -->> Cliente: 200 [...]
```

## Pontos de decisão relevantes

- **Mensagens de erro genéricas em 401**: não se distingue "não existe" de
  "inativo" para não permitir enumerar CPFs cadastrados (ver o README do
  repositório `oficina-auth-lambda`).
- **Verificação dupla de status**: `ClienteUserDetailsService` volta a checar
  `isAtivo()` a cada requisição autenticada — um cliente inativado depois de
  emitido o token deixa de conseguir usá-lo antes do `exp`, mesmo com
  assinatura válida.
- **Chave interna, não JWT**: a chamada do Lambda para
  `/internal/clientes/{cpf}/status` usa `X-Internal-Api-Key`, nunca um JWT —
  nesse ponto o cliente ainda não tem token nenhum.
