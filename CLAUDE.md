# CLAUDE.md — oficina (aplicação principal)

## Visão geral
API REST (Java 21, Spring Boot 4.0.6, Maven) de gestão de oficina mecânica: clientes, veículos,
ordens de serviço (OS), peças com estoque, pagamentos; JWT. Domínio, nomes e mensagens em **português**.

Projeto em 4 repositórios (diretórios irmãos em `oficina-project/`):
- `oficina` (este): app Spring Boot rodando no EKS; manifests em `k8s/` (Kustomize base + overlays).
- `oficina-auth-lambda`: Lambda Java que autentica cliente por CPF, chama `GET /internal/clientes/{cpf}/status` e emite JWT com `tipo=CLIENTE`.
- `oficina-infra-db`: Terraform do RDS PostgreSQL.
- `oficina-infra-k8s`: Terraform do EKS, API Gateway e observabilidade (Prometheus/Grafana).

## Arquitetura (como está hoje)
Pacote raiz `io.github.gabrielivo.oficina`; cada camada é subdividida por módulo
(`cliente`, `veiculo`, `ordemServico` — camelCase, `peca`, `pagamento`, `usuario`, `auth`).

| Camada | Responsabilidade | Pode depender de |
|---|---|---|
| `presentation` | `@RestController`, Request/Response (records), `*Mapper` (`@Component`) | application, domain (só p/ mapear), shared |
| `application` | `*Service` (`@Service`, `@Transactional`), `*Command` (records) | domain |
| `domain` | Entidades **JPA** (`@Entity`) com regras, enums, `*Exception`, `*Repository extends JpaRepository` | `jakarta.persistence`, Spring Data |
| `infrastructure` | segurança/JWT, adapters de notificação, filtros, seeder | domain, application |
| `shared` | `GlobalExceptionHandler`, `shared/util` (`@CpfValido`) | domain |
| `config` | beans de configuração (`OpenApiConfig`) | — |

- Direção: `presentation → application → domain`. `domain` nunca importa application/presentation/infrastructure.
- Decisão pragmática: entidades de domínio **são** entidades JPA e os repositories estendem `JpaRepository`
  diretamente (não há camada de persistência separada). Não introduzir mapeamento entidade↔JPA sem ADR.
- Regras verificadas em `src/test/java/.../architecture/ArquiteturaTest.java` (ArchUnit). Dívidas conhecidas,
  congeladas em `src/test/resources/archunit_store` (não replicar):
  `StatusNotificationPort` está em `infrastructure` mas é usado por `OrdemServicoService`;
  `AuthService` depende de `JwtService`; `PecaService` está em `application/ordemServico`;
  `PecaController` devolve a entidade `Peca`; ciclo `domain.peca ↔ domain.ordemServico` via `ItemPeca` (não usado).

## Padrão de caso de uso (referência: fluxo de cliente)
1. `application/<mod>/<Verbo><Coisa>Command` — record imutável (ex.: `CriarClienteCommand`).
2. Método no service (`ClienteService.criar`): busca/valida o que exige repositório, chama a entidade, salva.
   `@Transactional` (ou `readOnly = true` para consultas). Não-encontrado → `<Mod>Exception`.
3. Regra de negócio na entidade (`Cliente` construtor/`atualizar`/`inativar`); invariantes lançam
   `IllegalArgumentException`; estado só muda por método de negócio (sem setters).
4. `presentation/<mod>/`: `ClienteRequest` (record + Bean Validation), `ClienteResponse` (record),
   `ClienteMapper` (`toCommand`, `toResponse`), `ClienteController` (`@Valid`, chama service, devolve Response).
5. OpenAPI: `@Tag`/`@Operation` como em `OrdemServicoController`.

## Regras Spring Boot
- Injeção **só por construtor** (campos `private final`). Sem `@Autowired`/`@Value` em campo.
- `@Service` só para casos de uso em `application`; componente técnico em `infrastructure` usa `@Component`.
- `@Transactional` só em `application`.
- Controller nunca recebe nem devolve entidade: sempre Request/Response + Mapper.
- Controller não acessa `*Repository`; sempre via service.
- Exceção de domínio nova → handler em `shared/GlobalExceptionHandler` (padrão: 422 `{"erro": msg}`;
  `IllegalArgumentException` → 400; validação → 400; credenciais → 401).
- Configuração nova: `@ConfigurationProperties` (record) em vez de `@Value`; valores externos como
  `${ENV_VAR:default}` em `application.properties` (perfis `docker` e `k8s` sobrescrevem).
- Schema: hoje `spring.jpa.hibernate.ddl-auto=update` (**dívida**: não há Flyway/Liquibase). Toda mudança
  de entidade que altere tabela/coluna deve ser registrada em `documentacao/modelo_de_dados.md`.

## SOLID aplicado (regras verificáveis)
- **SRP**: Service orquestra (busca, chama entidade, salva, notifica); a **entidade decide** (validação de
  estado, transições, cálculos). Um `if` sobre estado da entidade dentro do service é cheiro — mova para a entidade.
- **SRP**: Controller só traduz HTTP ↔ Command/Response; Mapper só converte; nada de regra em ambos.
- **OCP**: variação de comportamento via nova implementação de interface (novo adapter de
  `StatusNotificationPort`, selecionado por `@ConditionalOnProperty`), não `if/else` por tipo no chamador.
- **LSP**: todo adapter implementa o contrato inteiro da porta (ver `NoopStatusNotificationPort`); nada de
  `UnsupportedOperationException`.
- **ISP**: portas pequenas, um propósito (uma porta = um tipo de integração, poucos métodos).
- **DIP**: integração externa (e-mail, HTTP, fila, token) entra no application **por interface**; porta nova
  fica em `application/<mod>/` (ou `domain`), adapter em `infrastructure/<area>/`. Service nunca referencia classe concreta de infra.

## Testes
- Entidade: teste unitário puro, sem Spring nem mocks (ex.: `OrdemServicoTest`, `PecaTest`).
- Service: Mockito (`@ExtendWith(MockitoExtension.class)`, repositories mockados), ex.: `PagamentoServiceTest`.
- Controller: `@WebMvcTest(XController.class)` + `MockMvc` + `@MockitoBean` do service/mapper.
  (Hoje os testes de controller existentes usam Mockito puro e alguns de service usam `@DataJpaTest`;
  código novo segue a regra acima.)
- Bug: primeiro escreva o teste que reproduz e falha; depois corrija.
- Nome de teste em português descrevendo o comportamento (`deveCriarClienteERetornarResponseCreated`).

## Comandos
```bash
./mvnw test                                   # testes
./mvnw test -Dtest="ClienteServiceTest#metodo" # um teste
./mvnw -q verify                              # verificação completa (testes, ArchUnit, spotless:check, jacoco:check)
./mvnw spotless:apply                         # formata (palantir-java-format) e remove imports não usados
./mvnw spring-boot:run                        # local: H2, segurança desligada, Swagger em /swagger-ui.html
docker compose up -d postgres mailhog         # Postgres + Mailhog (UI :8025)
```
Cobertura: relatório em `target/site/jacoco/index.html`; o `verify` falha com LINE < 84% (limite no `pom.xml`, só sobe).
CI (`.github/workflows/ci.yml`) e o hook `.githooks/pre-push` rodam o mesmo `./mvnw -q verify`.
Hooks do Claude Code (`.claude/settings.json`, requerem `jq`): após Edit/Write em `src/**/*.java` roda o Spotless
no arquivo; no Stop roda `./mvnw -q test-compile` e devolve os erros se não compilar (máx. 3 bloqueios seguidos).
Skill `/novo-caso-de-uso <módulo> <verbo> <coisa>` (`.claude/skills/novo-caso-de-uso/`): roteiro do padrão de caso de uso, com testes primeiro.
Admin seed: `admin`/`admin123` (`DataSeeder`). H2 console `/h2-console` (`jdbc:h2:mem:oficina`, `sa`).

**Antes de dizer que terminou: rode `./mvnw -q verify` e garanta que passa.** Se falhar, corrija ou relate a falha com a saída.

## Não faça
- O conteúdo de ADRs/RFCs aceitos (`documentacao/adrs`, `documentacao/rfcs`) não é alterado; apenas o campo
  Status pode ser atualizado para indicar substituição, referenciando o novo ADR. Decisão nova = ADR novo.
- Não adicione dependência no `pom.xml` sem justificar (por que, alternativa sem ela, escopo).
- Não mude sem avisar explicitamente: contrato de `/internal/**` (`ClienteInternalController`,
  `InternalApiKeyFilter`, header de API key), nem a claim `tipo` do JWT (`JwtService`, `TipoPrincipal`) —
  ambos são consumidos pela `oficina-auth-lambda`.
- Não edite `archunit_store/` à mão nem congele violação nova (`allowStoreCreation`/`refreeze`); corrija o código.
- Não corrija bugs ou refatore fora do escopo pedido; registre como sugestão.

## Referências
- Regras de domínio: @documentacao/regras_de_dominio.md
- Modelo de dados: `documentacao/modelo_de_dados.md`
- Decisões: `documentacao/adrs/` (REST síncrono, HPA, split em 4 repos), `documentacao/rfcs/` (nuvem, banco, auth por CPF)
- Diagramas: `documentacao/diagrama_componentes.md`, `documentacao/diagrama_sequencia_*.md`
- Infra/observabilidade: `documentacao/infraestrutura.md`; endpoints e exemplos: `README.md`
