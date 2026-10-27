# Decisões do Smart Van Pro

Fechadas em 27/10/2026 numa sessão de grilling (skill `grilling`) entre o Marcus e o orquestrador (Claude Opus 5.5).
O Smart Van Pro é o **mesmo produto** do Smart Van original (`../smart_van`), refeito com um orquestrador de agentes:
Opus coordena, agentes Sonnet escrevem o código. Especificação: `CONTEXTO_PROJETO_ORIGINAL.md` (26/10/2026) + este arquivo
+ `CONTRATO_API.md`. Onde houver conflito, vale este arquivo.

| # | Decisão |
|---|---|
| Escopo | Cinco casos de uso: **Buscar motorista, Contratação, Gerar rota, Mensalidades, Cancelamento**. |
| Q1 | Mesmas regras de negócio, schema e telas do original. Pendências do original já corrigidas: ADMINISTRADOR não é criado pelo cadastro público, `@Valid` + tratamento global de erros, nenhuma senha commitada, "Avaliações de motorista" fora do escopo. Enums marcados como "inventado" ficam como estão. |
| Q1b | Entidades e enums copiados do original (já batem com o schema); o resto é escrito do zero. |
| Q2 | Java 21 + Spring Boot 4.1 + Spring Security/JWT + Flyway + PostgreSQL. App: React Native + Expo Router + TypeScript. |
| Q3 | **Monolito modular**: um pacote por domínio (`auth`, `cadastro`, `contratacao`, `rota`, `financeiro`, `cancelamento`, `notificacao`), entidades compartilhadas em `model`. |
| Q4 | Mapas atrás da interface `MapsProvider`: Google quando houver `GOOGLE_MAPS_API_KEY`, senão plano B por proximidade (`otimizada = false`). Notificações só dentro do app, em tabela própria. Envio de documentos fora do escopo. |
| Q6 | **Docker e Kubernetes ficam fora do trabalho dos agentes.** Nenhum Dockerfile, compose ou manifest no repositório: o Marcus escreve tudo depois, guiado. |
| Q7 | Vídeo de apresentação (30–45 s) com HyperFrames, usando as telas reais do app. |
| Q8 | Waves: W0 orquestrador (base, schema, contrato) → W1a agente `base` → W1b agentes `contratacao`, `rota`, `mensalidades`, `cancelamento` em paralelo + W2 design system e telas → W3 vídeo → revisão por agente independente. |
| Q9 | `docs/` com diagramas PlantUML (código junto), contrato da API e `ALTERACOES_AGENTES.md`. |
| Q10 | Pré-requisitos mínimos: login JWT + criação de conta (responsável/motorista), cadastro de aluno com endereço e escola, motorista cadastra veículo, escolas atendidas e ponto de partida. Seed `dev` com motoristas `VALIDADO`. Validação por admin e documentos ficam fora. |
| Q11 | Calendário de faltas simples entra (a rota tira quem faltou). |
| Q12 | "Gerar rota" = gerar/reaproveitar a rota do dia (ida/volta), otimizar ou plano B, lista de paradas com horário previsto, notificar responsáveis. **Fora:** localização em tempo real e mapa desenhado. |
| Q13 | Vencimento no dia 10 (configurável). Agendador diário marca `ATRASADA`. Primeira mensalidade gerada no mês em que o contrato vira `ATIVO`. |
| Q14 | Telas: T01 Login, T02 Criar conta, cadastro de aluno, T07 Busca, T08 Perfil do motorista, T10 Detalhe do contrato, T11 Faltas, T12 Início do motorista, T13 Veículo e escolas, T15 Propostas, T16 Rota do dia, T17 Cobranças, Notificações, estados de lista. Admin (T18/T19) fora. Expo Web habilitado para revisão visual. |

## Decisões tomadas pelo orquestrador na Wave 0 (ao detalhar o contrato)

- **Valor da mensalidade:** o responsável propõe `valorMensal` na proposta e o motorista aceita ou recusa (o schema não tem preço por motorista).
- **Capacidade para vagas:** maior capacidade entre os veículos do motorista.
- **Aluno com contrato ativo** não pode receber outra proposta (`ALUNO_JA_TEM_CONTRATO`).
- **Reaproveitamento de rota:** só se o conjunto de alunos for o mesmo; se mudou (falta nova, contrato novo), a rota é regerada. Resolve a lacuna "rota reaproveitada não enxerga falta registrada depois" levantada em 26/10.
- **Tabelas novas:** `notificacoes` e `auditorias` (V2).
- **Seed:** nomes de escolas inventados em endereços reais de São Paulo.
- **Conta nasce ATIVA** para os dois papéis (decisão em aberto nº 3 do original).

## Mudança de plano em 27/10/2026 (bloqueio de rede)

O Maven Central está bloqueado na rede da sessão na nuvem e o Marcus não encontrou a configuração do ambiente.
Plano B híbrido, combinado com o Marcus:
- **Back-end:** orquestrado no Claude Code do computador do Marcus (onde o Maven funciona), com os agentes Sonnet de
  `.claude/agents/` e o prompt de `docs/PROMPT_ORQUESTRADOR.md`.
- **App e vídeo:** orquestrados na sessão da nuvem (npm funciona). O app tem um modo mock que segue o contrato até o
  back-end ficar pronto.
- A pasta `smart_van_pro` no computador do Marcus é a fonte da verdade; a sessão da nuvem entrega o app lá por commit.
