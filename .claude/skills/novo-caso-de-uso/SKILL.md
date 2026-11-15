---
name: novo-caso-de-uso
description: Cria um caso de uso novo no oficina seguindo o padrão do projeto (Command → Service → regra na entidade → Request/Response/Mapper/Controller), com testes primeiro. Use quando o pedido for adicionar uma operação/endpoint de negócio a um módulo (cliente, veiculo, ordemServico, peca, pagamento, usuario).
argument-hint: "<módulo> <verbo> <coisa> — ex.: veiculo transferir veiculo"
---

# Novo caso de uso

Siga o "Padrão de caso de uso" do `CLAUDE.md`. Referência viva: o fluxo de **cliente**.
Argumentos recebidos: `$ARGUMENTS` (se faltar módulo, verbo ou regra de negócio, pergunte antes de codar).

## 0. Entender antes de codar
- Leia `documentacao/regras_de_dominio.md` (seção do módulo) e o pacote do módulo em
  `domain/`, `application/` e `presentation/`.
- Defina em uma frase: **quem** chama, **qual regra** a entidade decide, **o que** é persistido, **qual** resposta HTTP.
- Se a regra conflitar com `regras_de_dominio.md` ou com um ADR, **pare e pergunte**.

## 1. Testes primeiro (devem falhar antes da implementação)
- **Entidade** — unitário puro, sem Spring nem mocks. Ex.: `src/test/java/.../domain/ordemServico/OrdemServicoTest.java`.
  Cubra caminho feliz, invariantes (`IllegalArgumentException`) e transição inválida.
- **Service** — `@ExtendWith(MockitoExtension.class)`, repositories mockados. Ex.: `.../application/cliente/ClienteServiceTest.java`.
  Cubra não-encontrado (`<Mod>Exception`) e o que é salvo/notificado.
- Nome do teste em português descrevendo o comportamento (`deveTransferirVeiculoParaOutroCliente`).

## 2. Application
- `application/<mod>/<Verbo><Coisa>Command.java` — `record` imutável (ex.: `CriarClienteCommand`).
- Método no `<Mod>Service` (ex.: `ClienteService.criar`): busca/valida o que depende de repositório,
  chama o método de negócio da entidade, salva. `@Transactional` (consulta: `readOnly = true`).
- Proibido: `if` sobre o estado da entidade no service (mova para a entidade); referência a classe concreta de infra.
- Integração externa (e-mail, HTTP, fila) entra **por porta** (interface pequena) em `application/<mod>/`;
  adapter `@Component` em `infrastructure/<area>/`, variações via `@ConditionalOnProperty`
  (modelo: `NoopStatusNotificationPort`). Não crie porta nova em `infrastructure/`.

## 3. Domain
- A regra vive na entidade (ex.: `domain/cliente/Cliente.java` — construtor, `atualizar`, `inativar`).
- Estado muda só por método com nome de negócio; **sem setters**.
- Invariante simples → `IllegalArgumentException` (400). Regra que precisa de repositório fica no service → `<Mod>Exception` (422).
- Mudou tabela/coluna? Registre em `documentacao/modelo_de_dados.md`.

## 4. Presentation
- `presentation/<mod>/`: `<Coisa>Request` (record + Bean Validation), `<Coisa>Response` (record),
  métodos no `<Mod>Mapper` (`toCommand`, `toResponse`), endpoint no `<Mod>Controller` com `@Valid`.
- Controller nunca recebe/devolve entidade e nunca acessa `*Repository`.
- OpenAPI: `@Tag`/`@Operation` como em `presentation/ordemServico/OrdemServicoController.java`.
- Teste de controller **novo**: `@WebMvcTest(<Mod>Controller.class)` + `MockMvc` + `@MockitoBean` do service/mapper
  (os testes antigos com Mockito puro não são modelo para código novo).
- Exceção de domínio nova → handler em `shared/GlobalExceptionHandler.java` (422 `{"erro": msg}`).

## 5. Regras que não se negociam
- Injeção só por construtor (`private final`); config nova via `@ConfigurationProperties` (record), nunca `@Value`.
- Não alterar contrato de `/internal/**` nem a claim `tipo` do JWT.
- Não editar `src/test/resources/archunit_store/` nem congelar violação nova — corrija o código.
- Dependência nova no `pom.xml` só com justificativa (por quê, alternativa sem ela, escopo).
- Fora do escopo pedido: registre como sugestão, não corrija.

## 6. Fechamento
1. Atualize `documentacao/regras_de_dominio.md` com a regra nova (comportamento **implementado**).
2. Rode `./mvnw -q verify` (testes, ArchUnit, Spotless, JaCoCo). Se falhar, corrija ou relate com a saída.
3. Opcional: peça ao subagent `revisor` para revisar o diff.
4. Resuma os arquivos criados/alterados e proponha a mensagem de commit (`feat(<mod>): ...`). Não faça commit nem push sem OK.
