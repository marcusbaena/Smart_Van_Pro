# Smart Van Pro — instruções para agentes

Responda e comente código em português. Este repositório é construído por vários agentes coordenados por um
orquestrador. Antes de qualquer tarefa leia, nesta ordem:

1. `docs/DECISOES.md` — o que foi decidido com o Marcus (vale sobre qualquer outro documento).
2. `docs/CONTRATO_API.md` — rotas, payloads, códigos de erro e regras. **Não mude o contrato**; se precisar, proponha em `docs/ALTERACOES_AGENTES.md`.
3. `docs/CONTEXTO_PROJETO_ORIGINAL.md` — seções 3 (convenções), 5 (regras de negócio) e 6 (schema).

## Estrutura

```
backend/   Spring Boot 4.1 (Java 21). Pacote base br.com.smartvan
  model/, enums/          entidades e enums compartilhados (copiados do original)
  repositorio/            CRUD compartilhado — NÃO EDITAR
  comum/                  erro, config (Clock, agendador), evento, maps, seguranca — NÃO EDITAR sem o orquestrador
  notificacao/, auditoria/  prontos (Wave 0)
  auth/ cadastro/ contratacao/ rota/ financeiro/ cancelamento/   um módulo por agente
  db/migration/           Flyway. Novas migrations: V<n>__<modulo>_<descricao>.sql, n combinado no briefing
mobile/    Expo Router + TypeScript
docs/      decisões, contrato, diagramas PlantUML, ALTERACOES_AGENTES.md
video/     HyperFrames
.claude/skills/  find-skills, grilling, grill-me, design-taste-frontend, hyperframes(+cli, registry), media-use
```

## Regras para agentes de back-end

- Trabalhe **só no seu módulo** (`br.com.smartvan.<modulo>`, com subpacotes `controller`, `service`, `dto`, `repository` se quiser) e nos testes dele.
- Consultas específicas: crie um repository no seu módulo (ex.: `contratacao/repository/ContratoBuscaRepository extends JpaRepository<Contrato, Long>`). Não edite `repositorio/` nem `model/`. Se uma entidade precisar mudar, pare e explique no relatório.
- Convenções do original (seção 3): DTOs como `record`, injeção por construtor, `@Valid` nos corpos, `BigDecimal` para dinheiro, `LocalDate.now(clock)` (nunca `LocalDate.now()` sem clock).
- Usuário atual: `UsuarioLogado.atual()`. Papel nas rotas: `@PreAuthorize("hasRole('MOTORISTA')")` etc.
- Erros: `RegraNegocioException` com os códigos do contrato. Nunca devolva entidade JPA na resposta.
- Notificar: `NotificacaoService.notificar(...)`. Auditar: `AuditoriaService.registrar(...)`.
- Agendadores: `@Scheduled(cron = "${smartvan.agendador.<x>.cron}", zone = "America/Sao_Paulo")` chamando um método público do service, que os testes chamam direto (o agendador fica desligado nos testes).
- Testes: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` (H2 modo PostgreSQL, Flyway real). Clock fixo via `@TestConfiguration` com `@Primary`. Um teste por regra do contrato, incluindo os caminhos de erro. Use os helpers de `backend/src/test/java/br/com/smartvan/suporte/` (criados pelo agente `base`).
- Rode `cd backend && mvn -q test` antes de terminar. **Não termine com teste falhando** nem com teste desligado (`@Disabled`) para passar.
- Commits no seu branch com prefixo `[FEAT]`, `[FIX]`, `[TEST]`, `[DOC]`, `[CHORE]`.
- Ao terminar, acrescente uma seção datada em `docs/ALTERACOES_AGENTES.md`: agente, arquivos criados, decisões tomadas, pendências.

## Economia de tokens (vale para todos os agentes e para o orquestrador)

O limite de uso do plano é compartilhado entre todas as sessões e agentes. Trabalhe enxuto:

- **Leia só o necessário:** a sua seção do `docs/CONTRATO_API.md` e os arquivos que vai mudar. Nada de ler `docs/CONTEXTO_PROJETO_ORIGINAL.md` inteiro, nem `docs/ALTERACOES_AGENTES.md` inteiro (só acrescente no fim).
- **Procure antes de abrir:** use busca (grep/glob) para achar a classe ou método; abra só o trecho que precisa (offset/limit). Não releia um arquivo que você acabou de escrever.
- **Saída de comando curta:** testes com `mvnw.cmd -q test`; se falhar, mostre só o fim (`... 2>&1 | Select-Object -Last 60` no PowerShell ou `| tail -60`). Nunca despeje logs inteiros, `node_modules`, `target/` ou arquivos gerados.
- **Rode só os testes do seu módulo** enquanto desenvolve (`-Dtest='br.com.smartvan.<modulo>.**'`); a suíte inteira uma vez no fim.
- **Sem screenshots nem imagens**, a não ser que a tarefa peça explicitamente.
- **Relatório final curto:** no máximo 15 linhas. Detalhes vão para `docs/ALTERACOES_AGENTES.md` (no máximo 20 linhas por seção).
- **Não explore o repositório "para conhecer".** O `CLAUDE.md`, a sua seção do contrato e o seu briefing bastam.

## Proibido

- Dockerfile, docker-compose, manifests Kubernetes, Helm, CI (decisão Q6 — o Marcus vai escrever).
- Chave do Google ou qualquer segredo no código. Chamar o Google em teste.
- Mudar `docs/CONTRATO_API.md`, `docs/DECISOES.md`, `comum/`, `repositorio/`, `model/` sem o orquestrador.
