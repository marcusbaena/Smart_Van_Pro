# Alterações feitas pelos agentes

Registro do que cada agente criou ou alterou, para o Marcus e a equipe saberem o que veio de IA e revisarem.
Orquestrador: Claude Opus 5.5. Agentes: Claude Sonnet 5.5.

---

## 27/10/2026 — Wave 0 (orquestrador)

- Monorepo criado: `backend/`, `mobile/`, `docs/`, `video/`, `.claude/skills/`.
- Skills instaladas em `.claude/skills/` (com as licenças): `find-skills` (vercel-labs/skills, MIT), `grilling` e `grill-me` (mattpocock/skills, MIT), `design-taste-frontend` (Leonxlnx/taste-skill, MIT), `hyperframes`, `hyperframes-cli`, `hyperframes-registry`, `media-use` (heygen-com/hyperframes, Apache-2.0).
- Copiados do original sem alteração: `model/*` (17 entidades) e `enums/*` (14 enums), `SmartvanBackendApplication`, Maven wrapper.
- `pom.xml`: + Spring Security, OAuth2 Resource Server (JWT/Nimbus), Flyway (+ PostgreSQL); − `spring-security-crypto` avulso (vem com o starter).
- Flyway `V1__schema_inicial.sql` (DBML de 26/10 inteiro) e `V2__notificacoes_auditoria.sql`. `ddl-auto: validate`.
- `comum/`: `RegraNegocioException` + `TratadorGlobalDeErros` (formato de erro do contrato), `RelogioConfig` (Clock São Paulo), `AgendadorConfig`, `ContratoAtivadoEvent`, porta `MapsProvider` + `MapsIndisponivel` (plano B quando não há chave), `UsuarioLogado`.
- Módulos prontos: `notificacao/` (entidade, service, controller) e `auditoria/`.
- `repositorio/`: CRUD compartilhado de todas as entidades usadas.
- `application.yml` sem senha commitada; perfil `dev` com segredo JWT só para desenvolvimento; teste em H2 modo PostgreSQL.
- Docs: `DECISOES.md`, `CONTRATO_API.md`, `CONTEXTO_PROJETO_ORIGINAL.md`, `CLAUDE.md` (regras dos agentes).

> **Ainda não compilado:** o Maven Central está bloqueado na rede desta sessão. Será compilado assim que liberado.

---

## 27/10/2026 — Wave 1a, agente `backend-base` (Claude Sonnet 5.5)

Branch `back/base`. `./mvnw test` verde (41 testes, nenhum `@Disabled`).

**Correções na Wave 0 (única mudança fora do escopo do módulo)**
- `V1__schema_inicial.sql`: `enderecos.uf` passou de `char(2)` para `varchar(2)` (o `ddl-auto: validate` do Hibernate
  esperava `varchar` e o `contextLoads` falhava). Nada foi mudado em `comum/`, `model/`, `repositorio/` nem no `pom.xml`;
  o starter `spring-boot-starter-oauth2-resource-server` e `HttpStatus.UNPROCESSABLE_CONTENT` já compilavam no Boot 4.1.1.

**Criado**
- `auth/`: `SegurancaConfig` (stateless, JWT HS256 Nimbus, `@EnableMethodSecurity`, CORS de `smartvan.cors.origens`, 401/403
  no JSON do contrato, falha se `smartvan.jwt.secret` < 32 caracteres), `JwtService`, `AuthService`, `AuthController`,
  DTOs e `UsuarioAuthRepository`.
- `cadastro/`: escolas, alunos do responsável, veículos, escolas atendidas, ponto de partida, perfil do motorista
  (controllers, services, DTOs, repositories de busca) e `seed/DadosDemonstracao` (`@Profile("dev")`, idempotente).
- Testes: `auth/AuthIntegracaoTest`, `cadastro/CadastroIntegracaoTest`, `cadastro/DadosDemonstracaoTest` e os helpers de
  `src/test/java/br/com/smartvan/suporte/` (ver `LEIA-ME.md` ali).

**Decisões / ambiguidades resolvidas**
- O decoder JWT valida `exp` com o `Clock` do negócio (e não com o relógio do sistema), para os testes poderem avançar o relógio.
- `tipo: ADMINISTRADOR` no cadastro é barrado por uma constraint própria (`@TipoCadastroPermitido`), erro em `campos.tipo`.
- E-mail é gravado em minúsculas e comparado sem caixa; placa é gravada em maiúsculas (aceita `abc1d23`).
- Conta com status diferente de `ATIVA` tentando logar recebe 403 `SEM_PERMISSAO` (o contrato não diz; hoje todas nascem ATIVA).
- `PUT /motorista/endereco-partida` atualiza o mesmo registro de endereço (mesmo `id`); sem coordenadas, geocodifica, e se
  não achar grava sem coordenada. `PUT /motorista/escolas` substitui a lista inteira; id inexistente -> 404.
- `GET /escolas` filtra por `contains` sem caixa (acentos não são normalizados).
- Aluno com `escolaId` inexistente -> 404 `NAO_ENCONTRADO`. Listagem de alunos devolve só vínculos `ATIVO`.
- Seed: se já existir qualquer usuário, não faz nada. Mensalidade do Carlos: competência do mês corrente, `PENDENTE`,
  vencimento dia 10 (ou hoje + 5 se já passou do dia 10). Escolas inventadas: Colégio Girassol (Pinheiros), Escola Aquarela
  (Vila Madalena), Instituto Horizonte (Perdizes); Lucas -> Girassol, Beatriz -> Horizonte, Pedro -> Aquarela;
  João atende Girassol e Aquarela; Marta atende Horizonte.
- Os helpers de teste usam `Clock`/beans `@Primary` via `@TestConfiguration` importada pela base; `Fabrica` e `Tokens` são
  `@Component` de teste (funcionam também em contextos que não usam a base).

**Pendências**
- Nenhuma de escopo. Observação: o seed nunca foi rodado contra PostgreSQL real (só H2).
