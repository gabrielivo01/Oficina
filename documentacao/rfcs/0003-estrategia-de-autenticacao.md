# RFC 0003: Estratégia de autenticação por CPF

- Status: Aceita
- Data: 2026-09-14

## Contexto

O desafio exige: proteger rotas sensíveis com autenticação via CPF, e uma
function serverless que valide o CPF, consulte existência/status do cliente,
e gere um JWT. O projeto já tinha um mecanismo de autenticação para staff
(login/senha, `AuthService`/`JwtService`/`JwtAuthFilter`), que precisa
continuar funcionando sem regressão.

## Decisão

1. O Lambda (`lambda-auth-cpf`) faz toda a validação de CPF e emissão de
   token — a aplicação principal nunca recebe um CPF bruto para autenticar
   diretamente.
2. O Lambda consulta a aplicação principal via um endpoint interno
   (`GET /internal/clientes/{cpf}/status`), em vez de acessar o banco
   diretamente — mantém a fronteira do domínio (`ClienteService` é o único
   dono da regra "o que é um cliente ativo").
3. O JWT emitido pelo Lambda usa o **mesmo segredo e algoritmo** (HS256) do
   `JwtService` da aplicação, com uma claim adicional `tipo=CLIENTE` que
   `JwtAuthFilter` usa para rotear a autenticação para
   `ClienteUserDetailsService` em vez de `UserDetailsServiceImpl` (login de
   staff, claim `tipo` ausente = compatibilidade retroativa).

## Alternativas consideradas

- **CPF autenticado diretamente pela aplicação principal** (sem Lambda):
  descartado porque o desafio pede explicitamente uma function serverless
  como parte da arquitetura, não apenas como wrapper redundante.
- **AWS Cognito com um custom authentication challenge por CPF**: mais
  "nativo" ao ecossistema AWS, mas exigiria migrar toda a gestão de sessão
  para Cognito (incluindo o fluxo de staff já existente) — descartado por
  escopo: o desafio pede uma function específica para autenticação por CPF,
  não uma migração completa do sistema de auth.
- **Lambda com acesso direto ao banco** (via VPC + credenciais próprias): mais
  rápido (uma chamada a menos), mas duplica a regra de negócio "cliente
  ativo" fora do domínio (`Cliente.isAtivo()`), e exige colocar o Lambda na
  mesma VPC do RDS com suas próprias credenciais de banco — mais superfície
  de segredo para gerenciar sem necessidade. Descartado em favor do endpoint
  interno.
- **Um único `UserDetailsService` cobrindo staff e clientes**: descartado —
  são dois modelos de credencial completamente diferentes (login/senha vs.
  CPF/status), e tentar unificá-los custou uma instabilidade séria durante o
  desenvolvimento (ver adiante).

## Consequências

- Dois tipos de JWT circulam pela aplicação (staff e cliente), diferenciados
  pela claim `tipo` — qualquer novo endpoint que precise diferenciar
  comportamento por tipo de principal deve inspecionar essa claim (hoje
  nenhum endpoint faz essa distinção; todos os autenticados têm o mesmo nível
  de acesso).
- **Risco real materializado**: publicar um segundo bean `UserDetailsService`
  (`ClienteUserDetailsService`) quebrou o autoconfig do Spring Security para
  o `AuthenticationManager` global, causando `StackOverflowError` em todo
  login de staff — só descoberto rodando a aplicação de verdade, não pelos
  testes unitários existentes (todos usam `AuthenticationManager` mockado).
  Corrigido publicando um `AuthenticationProvider` explícito em
  `SecurityConfig`, escopado só ao `UserDetailsService` de staff. Ver
  `documentacao/plano_implementacao.md`, seção 9, para os detalhes completos
  e o teste de regressão adicionado (`AuthenticationManagerWiringTest`).
- O endpoint interno (`/internal/**`) precisa de proteção própria
  (`InternalApiKeyFilter` + header `X-Internal-Api-Key`), já que não pode
  exigir um JWT que o próprio cliente ainda não possui nesse ponto do fluxo.
