# Prompt para continuar o back-end (versão econômica)

Use quando a sessão anterior parou no meio (limite de uso). Antes de colar:
- Dentro de `smart_van_pro`, rode `claude`.
- **Modelo:** para esta etapa (juntar branches, conferir testes) o **Sonnet** dá conta e gasta bem menos do limite que o
  Opus: `/model` → Sonnet. Se quiser manter o experimento "Opus orquestra", use Opus, sabendo que gasta mais.
- Copie a partir de "Você é o orquestrador".

---

Você é o orquestrador do back-end do Smart Van Pro, **continuando** um trabalho que parou no limite de uso. Siga a
seção "Economia de tokens" do `CLAUDE.md` à risca. Não releia documentos que não precisar; não refaça trabalho pronto.

Estado atual (confira com `git log --oneline main..back/<nome>` e `git -C ../svp-wt/rota status -s`):
- `main`: Wave 0 + Wave 1a (`backend-base`) prontas.
- Branches prontos e commitados: `back/contratacao`, `back/mensalidades`, `back/cancelamento` (worktrees em `../svp-wt/`).
- `back/rota`: **sem commit**. Veja se há trabalho não commitado em `../svp-wt/rota`.

Passos:
1. **Rota.** Se `../svp-wt/rota` tem arquivos alterados, dispare o agente `backend-rota` dizendo para **continuar a
   partir do que já está no worktree** (rodar os testes do módulo, completar o que falta, commitar), sem recomeçar. Se
   estiver vazio, dispare `backend-rota` normalmente no worktree `../svp-wt/rota`. Enquanto ele trabalha, faça o passo 2.
2. **Juntar** em `main`, um de cada vez, rodando `mvnw.cmd -q test` depois de cada merge (mostre só o fim se falhar):
   `back/contratacao` → `back/mensalidades` → `back/cancelamento` → `back/rota` (quando pronto).
   - Conflito em `docs/ALTERACOES_AGENTES.md`: manter as seções de todos, em ordem.
   - Unificar `ContratoCanceladoResponse` (cancelamento) no `ContratoResponse` da contratação e trocar a consulta de
     mensalidades do cancelamento por `MensalidadeConsultaService` — você mesmo, é mudança pequena.
   - Se um teste quebrar por causa de outro módulo, corrija você mesmo se for pequeno; se for grande, devolva ao agente
     dono com a mensagem de erro (só as linhas relevantes).
3. **Revisor** (`revisor`) uma vez, no `main` integrado. Corrija só achados graves/médios, mandando ao agente dono do
   módulo com a lista exata.
4. Só se ainda houver limite sobrando: `docs/diagramas/*.puml` (componentes + uma sequência por caso de uso, PlantUML)
   e `docs/openapi.yaml`. Se não houver, deixe anotado como pendência — a sessão da nuvem pode fazer depois.
5. Seção curta do orquestrador em `docs/ALTERACOES_AGENTES.md`, commit em `main`, e `git worktree prune`.

Relatório final para o Marcus (máx. 10 linhas): testes (total/falhas), o que foi integrado, achados do revisor
resolvidos/abertos, pendências. Termine com: "avise a sessão da nuvem que o back-end está pronto".
