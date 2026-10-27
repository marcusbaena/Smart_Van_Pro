# Smart Van Pro

Refação do **Smart Van** (projeto de Laboratório de Engenharia de Software — Mackenzie 2026.2) usando um
**orquestrador de agentes de IA**: Claude Opus 5.5 coordena, agentes Claude Sonnet 5.5 escrevem o código.

Plataforma mobile que conecta responsáveis a motoristas de transporte escolar. Casos de uso: **buscar motorista,
contratação, gerar rota, mensalidades e cancelamento**.

| Pasta | O que tem |
|---|---|
| `backend/` | Java 21 + Spring Boot 4.1, PostgreSQL, Flyway, JWT |
| `mobile/` | React Native + Expo Router + TypeScript (roda também no navegador) |
| `docs/` | Decisões, contrato da API, diagramas PlantUML, registro do que cada agente fez |
| `video/` | Vídeo de apresentação (HyperFrames) |
| `.claude/skills/` | Skills usadas: find-skills, grilling, design-taste-frontend, hyperframes |

Docker e Kubernetes ainda não estão aqui de propósito: fazem parte da trilha de estudo do Marcus.

## Integrantes do projeto original

Marcus Ruiz Nishimura Baena (10426044) · Gustavo Melo Silva (10438415) · Luiz Alberto Silva Mota (10436776) · Arthur Rodrigues Soares Lourenço (10434424)
