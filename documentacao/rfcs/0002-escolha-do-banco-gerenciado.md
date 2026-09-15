# RFC 0002: Escolha do banco de dados gerenciado

- Status: Aceita (reafirma decisão pré-existente do projeto)
- Data: 2026-09-14

## Contexto

O desafio pede "Banco de Dados Gerenciado (PostgreSQL, MySQL, SQL Server,
etc.)" com justificativa formal e ajustes no modelo relacional. O projeto já
usa PostgreSQL como banco principal (perfis `docker`/`k8s`,
`infra/terraform/modules/postgres-db`) e H2 apenas para desenvolvimento local
e testes. `pom.xml` também traz o driver `mysql-connector-j`, mas não há uso
ativo dele em nenhum profile — resquício, não uma segunda opção em produção.

## Decisão

Manter **PostgreSQL gerenciado via AWS RDS** como banco de produção.

## Justificativa formal

1. **Tipos de domínio já usados no modelo exigem boa cobertura**: o schema
   usa `enum` mapeado como `varchar` (`StatusOrdemServico`, `StatusCliente`,
   `TipoItemOrdemServico`, `FormaPagamento`) e `numeric(10,2)` para valores
   monetários (`Peca.preco`, `ItemOrdemServico.valor`,
   `OrdemServico.valorTotal`, `Pagamento.valor`). PostgreSQL lida bem com
   ambos sem armadilhas de precisão (ao contrário de `FLOAT`/`DOUBLE`, que o
   projeto já evita corretamente).
2. **Constraints e integridade referencial usadas de verdade**: há `UNIQUE`
   (`cliente.cpf`, `veiculo.placa`, `usuario.login`) e várias chaves
   estrangeiras com `NOT NULL` (`ordem_servico.cliente_id`,
   `item_ordem_servico.ordem_servico_id`, etc.) validadas pelo Hibernate mas
   que precisam ser garantidas pelo banco em concorrência — PostgreSQL aplica
   isso de forma correta e previsível sob carga concorrente (`SERIALIZABLE`/
   `READ COMMITTED` bem implementados), sem as particularidades históricas de
   MySQL com `SERIALIZABLE`/gap locks.
3. **Custo e maturidade no RDS**: instâncias PostgreSQL no RDS têm o mesmo
   modelo de custo/operação que MySQL no RDS (backups automáticos, Multi-AZ
   opcional, snapshots) — não há vantagem de custo em trocar, e a equipe já
   tem o módulo Terraform pronto e testado neste projeto.
4. **SQL Server descartado**: custo de licenciamento maior no RDS
   (license-included) sem nenhum requisito do domínio (ex. integração com
   stack .NET) que justifique o driver/licença adicional.

## Alternativas consideradas

- **MySQL no RDS**: viável tecnicamente, mas sem vantagem sobre PostgreSQL
  para este domínio, e a presença do driver `mysql-connector-j` no `pom.xml`
  não reflete uma decisão ativa — deve ser tratado como débito técnico a
  remover, não como opção real.
- **SQL Server no RDS**: descartado por custo de licenciamento sem benefício
  correspondente.
- **DynamoDB (NoSQL)**: descartado — o modelo é fortemente relacional (OS tem
  itens, itens referenciam peças, pagamento é 1:1 com OS) e depende de joins e
  transações multi-tabela (ex.: abrir OS decrementa estoque de peça na mesma
  transação); modelar isso em DynamoDB exigiria desnormalização e lógica de
  consistência que o Postgres já resolve nativamente.

## Consequências

- Nenhuma mudança de schema é necessária além do que já foi feito nesta
  rodada de trabalho (`StatusCliente`, `OrdemServico.statusDesde`) — ambos são
  colunas simples, sem incompatibilidade com PostgreSQL.
- Recomenda-se remover a dependência `mysql-connector-j` do `pom.xml` em uma
  limpeza futura, já que não há profile que a utilize.
- Ver `documentacao/modelo_de_dados.md` para o diagrama ER atualizado e a
  explicação dos relacionamentos entre os módulos.
