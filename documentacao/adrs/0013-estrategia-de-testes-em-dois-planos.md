# ADR 0013: Estratégia de testes em dois planos

- Status: Aceita
- Data: 2026-11-16

> O plano de implementação (`plano_fase4_implementacao.md`, Fase 0) previa este ADR com o número 0010. Esse número
> já tinha sido usado pelo ADR de evento de status e notificação assíncrona; por isso a decisão ficou como 0013.

## Contexto

A Fase 4 divide o sistema em três serviços (OS, Execution, Billing) que conversam por RabbitMQ, com PostgreSQL e
MongoDB próprios, Mercado Pago, tracing e deploy no kind. Validar tudo isso com infraestrutura real em cada etapa
exige Docker, containers e cluster, deixa o `verify` lento (minutos de Testcontainers por repositório) e não cabe na
máquina de desenvolvimento (WSL com ~4 GB). Ainda assim, a lógica de domínio, a Saga e os contratos precisam de
uma rede de segurança rápida, que rode em qualquer máquina e a cada commit.

## Decisão

Os testes ficam em dois planos:

1. **Plano 1 — implementação (`./mvnw verify`, sem Docker nem rede).** Em todos os repositórios:
   - domínio com testes unitários puros; application com Mockito; presentation com `@WebMvcTest`/MockMvc;
   - repositórios JPA com consulta própria em `@DataJpaTest` sobre H2 em modo PostgreSQL, com as migrations Flyway;
   - código MongoDB próprio com `MongoTemplate` mockado; publicadores do outbox com `RabbitTemplate` mockado;
   - listeners AMQP testados como unidades que delegam ao serviço de aplicação;
   - **testes de contrato**: cada serviço desserializa os exemplos JSON do catálogo
     (`documentacao/contratos_mensagens.md`, copiados em `src/test/resources/contratos/`) e serializa os eventos
     que produz;
   - adapter do Mercado Pago com WireMock (na JVM);
   - **Saga em memória** (OS Service): orquestrador, outbox e inbox reais sobre repositórios em memória e um
     barramento em memória com participantes falsos de Execution e Billing (`src/test/java/.../saga/memoria`);
   - **BDD** (Cucumber, `# language: pt`) sobre essa Saga em memória. Os steps falam com a interface
     `ComunicacaoSaga`, para o Plano 2 reaproveitar os mesmos cenários contra os serviços reais;
   - um `@SpringBootTest` por serviço, sem broker nem banco externo, só para garantir o wiring.
2. **Plano 2 — integração (`./mvnw verify -Pintegracao` e ambiente kind).** Testes `*IT` (Testcontainers com
   PostgreSQL, RabbitMQ e MongoDB reais), executados pelo `maven-failsafe-plugin` no perfil `integracao`, que não
   faz parte do `verify` padrão. Também ficam no Plano 2 o Compose e o kind de pé, o CD com smoke test E2E, os traces
   no Jaeger e o Mercado Pago sandbox.

O CI roda os dois: o job principal executa `./mvnw verify` (com JaCoCo ≥ 80% e SonarCloud) e um job separado
executa `./mvnw verify -Pintegracao` no runner do GitHub, que tem Docker.

## Alternativas consideradas

- **Testcontainers no `verify` padrão:** valida mais cedo, mas exige Docker em toda máquina, deixa o ciclo lento e
  falha por motivo de infraestrutura (memória, rede) em vez de regressão de código.
- **Só testes com mocks, sem integração:** rápido, mas não detecta erro de mapeamento, de migration no PostgreSQL
  real, de topologia RabbitMQ ou de serialização no broker.
- **Biblioteca compartilhada de contratos entre os repositórios:** daria tipos únicos, mas acopla o ciclo de
  release dos serviços. Preferimos copiar os exemplos do catálogo e testar contra eles (regra 6 do plano).

## Consequências

- `./mvnw verify` roda em poucos minutos e sem Docker em qualquer repositório.
- Uma divergência entre H2 e PostgreSQL, ou entre a topologia declarada e o broker, só aparece no Plano 2 (job de
  integração do CI ou execução local com `-Pintegracao`).
- Testes novos que precisem de container devem terminar em `IT`; o surefire os ignora e o failsafe os executa.
- A Saga em memória precisa acompanhar o catálogo de contratos: os participantes falsos respondem com os exemplos
  JSON, então um contrato alterado sem atualizar o exemplo quebra o teste.
