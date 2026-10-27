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
