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

## 2026-10-27 — agente `contratacao` (Wave 1b) — Buscar motorista (UC05) e Contratação (UC06)

**Criado** (`backend/src/main/java/br/com/smartvan/contratacao/`)
- `controller/ContratacaoController` (7 endpoints da seção 2), `service/ContratacaoService`.
- `repository/`: `MotoristaBuscaRepository` (JPQL com `statusValidacao = VALIDADO` e filtros na consulta),
  `ContratoBuscaRepository`, `CapacidadeRepository` (veículos por capacidade, vínculos responsável-aluno).
- `dto/`: `MotoristaBusca`, `PropostaRequest`, `ContratoResponse`, `ContratoMapper`, `AlunoResumo`, `UsuarioResumo`.
- Testes: `ContratacaoIntegracaoTest` (um teste por regra e por código de erro, eventos com `@RecordApplicationEvents`).

**Decisões onde o contrato era vago**
- Ordem das validações da proposta: aluno existe (404) -> aluno é do responsável (403) -> motorista existe (404) ->
  `MOTORISTA_NAO_VALIDADO` -> `SEM_VAGAS` -> `PROPOSTA_DUPLICADA` -> `ALUNO_JA_TEM_CONTRATO`.
- Vínculo "aluno do responsável" = `responsaveis_alunos` com `status_vinculo = ATIVO`.
- `GET /motoristas/{id}` de motorista não VALIDADO ou inexistente -> 404; também exige >= 1 aluno (422 `ALUNO_OBRIGATORIO`).
- Motorista sem veículo: `veiculo` null, `vagasRestantes` 0 (proposta -> 409 `SEM_VAGAS`).
- Ao aceitar, além de `SEM_VAGAS`, rechecagem de `ALUNO_JA_TEM_CONTRATO` (aluno ativado por outro motorista entre proposta e aceite).
- `cidade`/`nome`: `contains` case-insensitive, sem normalizar acento (mesmo critério de `GET /escolas`).
- `valorMensal` precisa ser > 0, até 2 casas.
- `ContratoResponse.escola` = escola do aluno; `cancelamento` é `null` (chave presente) quando não há solicitação.
- Resumos de aluno/usuário ficaram em `contratacao/dto` (`AlunoResumo`, `UsuarioResumo`); `EscolaResumo` é o de `cadastro/dto`.

**Para o agente de cancelamento**: use `ContratoMapper.de(Contrato)` (estático, retorna `ContratoResponse`). Chame dentro de
`@Transactional` (associações lazy). O bloco `cancelamento` sai preenchido quando `dataSolicitacaoCancelamento != null`.

**Pendências**: nenhuma.
---

## 2026-10-27 — agente backend-mensalidades (Wave 1b)

**Criados** (`br.com.smartvan.financeiro`): `controller/MensalidadeController`, `service/MensalidadeService`,
`service/MensalidadeJob`, `service/MensalidadeConsultaService`, `dto/MensalidadeResponse`, `dto/BaixaRequest`,
`repository/MensalidadeBuscaRepository`; teste `financeiro/MensalidadeIntegracaoTest` (22 testes). Sem migration.

**Decisões**
- Listener do `ContratoAtivadoEvent` é `@EventListener` + `@Transactional`: participa da transação de quem publica
  (a mensalidade nasce junto com o aceite) e abre a própria se publicado fora de transação. Idempotente.
- Vencimento: dia 10 da competência; se hoje > dia 10, hoje + 5 (vale para evento e job; no dia 10 ainda vence no dia 10).
- Job: `gerarCompetenciaAtual()` gera uma mensalidade por transação (falha de um contrato não derruba os demais);
  `marcarAtrasadas()` só atua em `PENDENTE`, então a notificação sai uma vez. `executar()` é o `@Scheduled`.
- `MensalidadeConsultaService` está em `br.com.smartvan.financeiro.service`.
- Ordem dos erros na baixa: 404, 403, 409, 422. `GET /contratos/{id}/mensalidades`: 404 se contrato inexistente, 403 se não for parte.
- `competencia` inválida em `/motorista/cobrancas` -> 400 `VALIDACAO`.
- `AlunoResumo`/`UsuarioResumo` ficaram como records aninhados em `MensalidadeResponse` (ainda não existiam no código comum).

**Pendências:** nenhuma.
## 2026-10-27 — backend-cancelamento (Wave 1b)

**Criado** (`br.com.smartvan.cancelamento`): `controller/CancelamentoController`, `service/CancelamentoService`,
`service/CancelamentoJob`, `repository/CancelamentoRepository`, `dto/CancelamentoRequest`, `dto/ContratoCanceladoResponse`;
teste `CancelamentoIntegracaoTest` (13 testes).

**Decisões**
- `POST /contratos/{id}/cancelamento` devolve **200** com o `Contrato`. Contrato inexistente -> 404.
- `ContratoCanceladoResponse` espelha o `Contrato` da seção 2 (provisório). Montado num único método privado
  `CancelamentoService.montarResposta`.
- Pendência de mensalidade via `CancelamentoRepository.contarPendentesOuAtrasadas`, atrás de um único método privado
  `existePendenteOuAtrasada` (ponto de troca por `MensalidadeConsultaService`).
- O job lista ids vencidos (`idsVencidos`) e chama `encerrar(id)` pelo proxy, uma transação por contrato (uma falha não
  barra os demais). `CancelamentoJob.encerrarVencidos()` é o método público chamado pelos testes.
- Sem migration nova.

**Pendências / merge**: trocar `ContratoCanceladoResponse` pelo `ContratoResponse` oficial; opcionalmente trocar a consulta de
pendência por `MensalidadeConsultaService`.
## 2026-10-27 — backend-rota (back/rota)
- Criado `br.com.smartvan.rota`: `controller` (Falta, Rota), `dto`, `repository` (consultas próprias), `service` (FaltaService, RotaService), `maps` (GoogleMapsProvider, GoogleMapsConfig, OrdenadorPorProximidade).
- Endpoints: `POST/GET/DELETE /alunos/{id}/faltas`, `POST /motorista/rotas/gerar`, `GET /motorista/rotas`, `GET /motorista/rotas/{id}`.
- Plano B: qualquer exceção do `MapsProvider.otimizarRota` -> vizinho mais próximo (haversine), `otimizada=false`, distância x1,3, 25 km/h, polyline nula.
- Google: Routes API (limite de 25 paradas, senão cai no plano B) e Geocoding via RestClient, timeout 5 s; só é registrado com `smartvan.google-maps.api-key` não vazio (variável de ambiente `GOOGLE_MAPS_API_KEY`, conforme o application.yml). Chave nunca é logada.
- Testes: 54 no módulo (integração com `TesteIntegracaoBase`, parsing do Google com `MockRestServiceServer`; nada chama o Google).
- Sem migration nova. Sem mudanças em model/repositorio/comum.
- Pendência: nenhuma conhecida; suíte completa deve ser rodada pelo orquestrador após o merge.
