# Prompt do orquestrador — back-end do Smart Van Pro (Claude Code local)

## Antes de colar

1. Abra um terminal **na pasta `smart_van_pro`** (a raiz do repositório, onde está o `CLAUDE.md`).
2. Confira o Java: `java -version` precisa mostrar **21**. Não precisa instalar Maven: o projeto usa o `mvnw`.
3. Rode `claude` e escolha o modelo do orquestrador: `/model` → **Opus 5.5**. Os agentes de `.claude/agents/` já estão
   configurados com `model: sonnet`.
4. Confira que o Claude Code enxergou os agentes: `/agents` deve listar `backend-base`, `backend-contratacao`,
   `backend-rota`, `backend-mensalidades`, `backend-cancelamento` e `revisor`.
5. Cole o prompt abaixo.

---

## Prompt (copie daqui até o fim)

Você é o **orquestrador** do back-end do Smart Van Pro. Você coordena agentes Sonnet definidos em `.claude/agents/` e
**não escreve as funcionalidades você mesmo**: divide, dispara, confere, integra. Responda em português.

Leia primeiro `CLAUDE.md`, `docs/DECISOES.md`, `docs/CONTRATO_API.md` e `docs/ALTERACOES_AGENTES.md`. A Wave 0 (schema
Flyway, contrato, `comum/`, notificações, entidades) já está commitada em `main`, mas **nunca foi compilada** (a sessão
que a escreveu não tinha acesso ao Maven Central).

Regras que valem para você e para todos os agentes:
- Não mexa em `mobile/` nem em `video/` (estão sendo feitos em outra sessão, na nuvem).
- Nada de Dockerfile, docker-compose, Kubernetes ou CI (decisão Q6: o Marcus vai escrever isso, guiado).
- Não faça push para o GitHub. Commits locais só.
- Use `mvnw.cmd` no Windows (ou `./mvnw` no Git Bash).

### Wave 1a — fundação (sequencial)
1. Dispare o agente **`backend-base`** e espere terminar.
2. Confira você mesmo: `cd backend && mvnw.cmd test` verde; leia o diff; confira que os endpoints da seção 1 batem com o
   contrato (rotas, campos, códigos de erro) e que existe `backend/src/test/java/br/com/smartvan/suporte/LEIA-ME.md`.
3. Se algo estiver errado, devolva ao mesmo agente com a lista exata do que corrigir. Quando estiver certo, commit em
   `main` (se o agente trabalhou em branch, faça o merge).

### Wave 1b — casos de uso (em paralelo)
4. Dispare **ao mesmo tempo**, cada um isolado em seu próprio git worktree (parâmetro de isolamento `worktree` da
   ferramenta de agentes; se não estiver disponível, crie `git worktree add ../svp-wt/<nome> -b back/<nome>` e diga ao
   agente para trabalhar lá), em segundo plano:
   `backend-contratacao`, `backend-rota`, `backend-mensalidades`, `backend-cancelamento`.
5. Quando cada um terminar: rode os testes no worktree dele, leia o diff contra a seção do contrato que é dele e devolva
   correções se precisar.
6. Integre em `main` um de cada vez (merge do branch). Conflitos esperados e como resolver:
   - `docs/ALTERACOES_AGENTES.md`: manter as seções de todos, em ordem.
   - `ContratoCanceladoResponse` (cancelamento) × `ContratoResponse` (contratação): unificar no `ContratoResponse` da
     contratação, para os dois endpoints devolverem o mesmo objeto. Ajuste os testes do cancelamento.
   - Se cancelamento consulta mensalidades pelo próprio repository, troque por `MensalidadeConsultaService` do financeiro.
   Depois de cada merge: `mvnw.cmd test` 100% verde antes do próximo.
7. Teste de ponta a ponta no `main` integrado: um teste de integração novo (`FluxoCompletoTest`) que faz busca → proposta
   → aceite → mensalidade gerada → rota gerada → cancelamento → job de encerramento. Pode pedir a um dos agentes.

### Revisão independente
8. Dispare o agente **`revisor`** no `main` integrado. Ele escreve `docs/REVISAO_BACKEND.md` e não corrige nada.
9. Para cada achado grave ou médio, mande o agente dono do módulo corrigir (em worktree), confira e integre. Rode o
   `revisor` de novo se as correções forem grandes.

### Documentação final (você mesmo)
10. `docs/diagramas/`: em **PlantUML, com o código `.puml` versionado** (preferência do Marcus) — diagrama de
    componentes do monolito modular e um diagrama de sequência para cada caso de uso (buscar motorista, contratação,
    gerar rota, mensalidades, cancelamento), fiéis ao código que ficou.
11. `docs/openapi.yaml` escrito a partir dos controllers reais (OpenAPI 3.1).
12. Atualize `docs/ALTERACOES_AGENTES.md` com uma seção do orquestrador: o que cada agente entregou, quantos testes,
    o que você unificou, achados do revisor e como ficaram.
13. Commit final em `main`.

### Relatório final para o Marcus
Curto: número de testes, endpoints prontos, achados do revisor resolvidos/abertos, quanto cada agente custou de
tempo, e o lembrete: **"avise a sessão da nuvem que o back-end está pronto"** — ela vai ligar o app à API real.
