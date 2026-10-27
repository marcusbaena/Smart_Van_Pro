# Smart Van — Contexto do Projeto

> Documento de referência para a equipe e para sessões com IA (Claude).
> Última atualização: 26/10/2026.
> Histórico de alterações feitas pelo Claude: ver [ALTERACOES_CLAUDE.md](ALTERACOES_CLAUDE.md).

---

## 1. Visão geral

O **Smart Van** é uma plataforma mobile que conecta **responsáveis (pais)** a **motoristas de transporte escolar**.

- **Responsável:** cadastra filhos, endereços e escolas; busca motoristas validados (por escola, cidade ou nome); envia proposta de contratação; acompanha mensalidades; registra faltas do aluno; acompanha a rota em tempo real.
- **Motorista:** cadastra veículo, escolas atendidas e documentos; aceita/recusa propostas; gera a rota do dia (otimizada via serviço de mapas); emite cobranças e dá baixa em mensalidades.
- **Administrador:** analisa os documentos dos motoristas e valida/rejeita o cadastro. Só motorista **VALIDADO** aparece na busca.

Projeto acadêmico — Universidade Presbiteriana Mackenzie, Ciência da Computação.

| Integrante | RA | Responsabilidade |
|---|---|---|
| Marcus Ruiz Nishimura Baena | 10426044 | Banco de dados e backend |
| Gustavo Melo Silva | 10438415 | — |
| Luiz Alberto Silva Mota | 10436776 | — |
| Arthur Rodrigues Soares Lourenço | 10434424 | — |

---

## 2. Stack e ferramentas

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1 (webmvc, data-jpa, validation, actuator), Lombok, spring-security-crypto (BCrypt) |
| Banco | PostgreSQL (dev/prod), H2 em memória (testes) |
| Frontend | React Native + Expo 57 + Expo Router (TypeScript) |
| Infra (planejada) | AWS, Docker, Kubernetes, Jenkins (CI/CD), Postman, Git/GitHub |
| Mapas (decidido em 21/10/2026) | Google Maps Platform: Routes API + Geocoding API (Route Optimization API se houver mais de uma escola por viagem) |

**Pré-requisito para rodar o backend:** JDK 21 (não basta JRE). Testes: `./mvnw test` dentro de `backend/`.

---

## 3. Arquitetura

**Documentada:** microserviços em Kubernetes com API Gateway/BFF, comunicação REST. Serviços previstos: Rotas, Contratos, Mensalidades, Autenticação/Usuários, Motoristas, Validação de Documentos, Responsáveis/Alunos; pods de suporte: Mapas, Notificações, E-mail. PostgreSQL + armazenamento de objetos (S3, bucket privado com URL assinada) para documentos.

**Implementada até agora:** um único backend Spring Boot (monolito).

> **Decisão em aberto:** seguir microserviços como na documentação ou adotar **monolito modular** (pacotes por domínio, várias réplicas no Kubernetes atendem o REQ09). Recomendação discutida: monolito modular, extraindo 1–2 serviços (ex.: notificações) se a avaliação exigir.

### Estrutura do backend (`backend/src/main/java/br/com/smartvan/`)

| Pacote | Objetivo |
|---|---|
| `config` | Beans de configuração (`SenhaConfig` → `BCryptPasswordEncoder`) |
| `controller` | Endpoints REST |
| `dto` | O que a requisição envia e o que a resposta retorna (records) |
| `enums` | Tipos e estados |
| `model` | Entidades JPA |
| `repository` | Acesso ao banco (Spring Data JPA) |
| `service` | Regras de negócio |

### Convenções de código

- Entidades com Lombok `@Getter` / `@Setter` (não usar `@Data` em entidades).
- Campo de ID no Java sempre `id`, com o nome real da coluna no `@Column` (ex.: `@Column(name = "usuario_id") private Long id;`).
- IDs como `Long` (wrapper), nunca `long` primitivo.
- `@ManyToOne(fetch = FetchType.LAZY)` em todos os relacionamentos.
- Enums persistidos como texto: `@Enumerated(EnumType.STRING)`.
- Datas: `Instant` para timestamp, `LocalDate` para date, `LocalTime` para time, `BigDecimal` para valores monetários.
- Herança de usuários: `@Inheritance(JOINED)` — `usuarios` + tabela por papel (`motoristas`, `responsaveis`, `administradores`) ligadas por `usuario_id`.
- Injeção de dependência por construtor.
- DTOs como `record`.
- Commits com prefixo: `[FEAT]`, `[FIX]`, `[DOC]`, `[CHORE]`, `[TEST]`.

---

## 4. Requisitos

| ID | Tipo | Descrição | Prioridade |
|---|---|---|---|
| REQ01 | RF | Cadastro de informações do usuário | Alta |
| REQ02 | RF | Responsável: cadastrar filhos, buscar e contratar motoristas, consultar mensalidade | Alta |
| REQ03 | RF | Responsável e motorista podem solicitar cancelamento | Média |
| REQ04 | RF | Usuários editam seus perfis | Média |
| REQ05 | RF | Usuários autorizados visualizam a rota otimizada em tempo real | Média |
| REQ06 | RF | Motorista gerencia a rota (menor tempo ou personalizada) e emite cobranças | Média |
| REQ07 | RNF | Dados sensíveis armazenados de forma segura | Média |
| REQ08 | RNF | Localização visível só para o responsável e o motorista | Média |
| REQ09 | RNF | Escalabilidade via cluster Kubernetes | Média |
| REQ10 | RNF | Disponibilidade durante o horário de funcionamento | Alta |
| REQ11 | RNF | Localização apresentada em tempo adequado | Alta |

---

## 5. Regras de negócio (extraídas dos diagramas de sequência)

### Busca de motoristas (UC05)
- O responsável precisa ter **ao menos um aluno cadastrado** para buscar.
- Só retorna motoristas com `status_validacao = VALIDADO` (condição obrigatória na consulta).
- Filtros: escola, cidade, nome.
- **Vagas restantes** = capacidade do veículo − contratos `ATIVO` do motorista.

### Proposta / contratação (UC06)
- A proposta **é um `Contrato` com status `PENDENTE`** (não existe tabela de proposta).
- Ao criar: verificar se o aluno pertence ao responsável; se o motorista tem vaga; se já não existe proposta `PENDENTE` para o mesmo aluno + motorista. Notifica o motorista.
- Ao aceitar: rechecar vagas → `ATIVO`, `data_inicio = hoje`, notificar responsável.
- Ao recusar: `RECUSADO`, notificar responsável.
- Cada contrato refere-se a **um único aluno**. Responsável com dois filhos = dois contratos.

### Rota do dia (UC15)
- Usa os contratos `ATIVO` do motorista e remove os alunos com **falta registrada** na data.
- Se já existe rota válida para o dia, reaproveita.
- Chama o serviço de mapas para ordenar as paradas e estimar tempo. Se falhar: ordena por proximidade e salva com `otimizada = false` (tela mostra aviso "última ordem calculada").
- Após salvar, notifica os responsáveis com o tempo estimado.
- **Ainda não implementado.** Uma primeira versão (21/10) foi desfeita em 26/10 para ser refeita passo a passo; o código está no `git stash` "backup 26/10: integracao Google Maps (rota do dia) + docs, antes de refazer passo a passo".

### Mensalidades (UC10)
- **Geração automática** (agendador, sem requisição HTTP): para cada contrato `ATIVO`, cria a mensalidade da competência se ainda não existir (`PENDENTE`) e notifica o responsável.
- **Baixa manual** pelo motorista: valida que o contrato é dele; se já `PAGA` → erro; senão marca `PAGA` + `data_pagamento`, registra auditoria e notifica.
- Status: `PENDENTE`, `PAGA`, `ATRASADA`.

### Cancelamento
- Solicitante (responsável ou motorista) precisa ser parte do contrato; contrato precisa estar `ATIVO`; não pode haver solicitação anterior.
- **Aviso prévio de 30 dias:** grava `data_solicitacao_cancelamento`, `data_encerramento_efetivo = hoje + 30`, `solicitante_cancelamento`, `motivo`. O status **continua `ATIVO`** e as mensalidades continuam sendo geradas.
- Agendador, na data efetiva: `ENCERRADO`; se houver mensalidades pendentes, notifica as partes.

### Validação de motorista
- Documentos exigidos: `CNH`, `COMPROVANTE_RESIDENCIA`, `FICHA_CRIMINAL`, `VISTORIA_VEICULO` (vistoria ligada a um veículo).
- Status de documento: `EM_ANALISE`, `APROVADO`, `REJEITADO` (exige motivo), `VENCIDO`.
- O administrador registra uma `Validacao` com decisão `VALIDADO` ou `REJEITADO`; **parecer obrigatório ao rejeitar**.
- Motorista nasce com `status_validacao = PENDENTE`.

---

## 6. Banco de dados (schema atual em DBML)

Inclui as tabelas `motoristas_escolas` e `faltas`, adicionadas em 19/10/2026 (não estavam no schema original, mas são exigidas pela busca por escola e pelo calendário de faltas).

```dbml
Table usuarios {
	usuario_id bigint [ pk, increment, not null ]
	email varchar(255) [ not null, unique ]
	senha_hash varchar(255) [ not null ]
	nome varchar(255) [ not null ]
	cpf varchar(11) [ not null, unique ]
	telefone varchar(20) [ not null ]
	tipo varchar(30) [ not null, note: 'RESPONSAVEL | MOTORISTA | ADMINISTRADOR' ]
	status varchar(30) [ not null ]
	data_criacao timestamp [ not null ]
	ultimo_acesso timestamp
}

Table motoristas {
	usuario_id bigint [ pk, not null ]
	status_validacao varchar(30) [ not null ]
	endereco_partida_id bigint [ note: 'de onde a van sai na ida e para onde volta' ]
}

Table responsaveis {
	usuario_id bigint [ pk, not null ]
	endereco_id bigint
}

Table administradores {
	usuario_id bigint [ pk, not null ]
}

Table enderecos {
	endereco_id bigint [ pk, increment, not null ]
	logradouro varchar(255) [ not null ]
	numero varchar(20) [ not null ]
	complemento varchar(100)
	bairro varchar(100) [ not null ]
	cidade varchar(100) [ not null ]
	uf char(2) [ not null ]
	cep varchar(8) [ not null ]
	latitude decimal(10,7)
	longitude decimal(10,7)
}

Table escolas {
	escola_id bigint [ pk, increment, not null ]
	endereco_id bigint [ not null ]
	nome varchar(255) [ not null ]
	telefone varchar(20)
}

Table alunos {
	aluno_id bigint [ pk, increment, not null ]
	escola_id bigint
	nome varchar(255) [ not null ]
	data_nascimento date [ not null ]
	serie varchar(30)
}

Table alunos_enderecos {
	aluno_id bigint [ pk, not null ]
	endereco_id bigint [ pk, not null ]
	tipo varchar(30) [ not null ]
	principal boolean [ not null ]
}

Table responsaveis_alunos {
	responsavel_id bigint [ pk, not null ]
	aluno_id bigint [ pk, not null ]
	parentesco varchar(30) [ not null ]
	status_vinculo varchar(30) [ not null ]
	acompanha_rota boolean [ not null ]
}

Table veiculos {
	veiculo_id bigint [ pk, increment, not null ]
	motorista_id bigint [ not null ]
	placa varchar(7) [ not null, unique ]
	modelo varchar(100) [ not null ]
	ano integer [ not null ]
	capacidade integer [ not null ]
	tipo varchar(30) [ not null ]
}

Table motoristas_escolas {
	motorista_id bigint [ pk, not null ]
	escola_id bigint [ pk, not null ]
}

Table documentos {
	documento_id bigint [ pk, increment, not null ]
	motorista_id bigint [ not null ]
	veiculo_id bigint
	tipo varchar(30) [ not null ]
	chave_arquivo varchar(255) [ not null ]
	data_envio timestamp [ not null ]
	validade date
	status varchar(30) [ not null ]
	motivo_recusa text
}

Table validacoes {
	validacao_id bigint [ pk, increment, not null ]
	administrador_id bigint [ not null ]
	motorista_id bigint [ not null ]
	data_validacao timestamp [ not null ]
	decisao varchar(30) [ not null ]
	parecer text
}

Table contratos {
	contrato_id bigint [ pk, increment, not null ]
	responsavel_id bigint [ not null ]
	motorista_id bigint [ not null ]
	aluno_id bigint [ not null ]
	valor_mensal decimal(10,2) [ not null ]
	data_inicio date [ not null ]
	status varchar(30) [ not null ]
	data_solicitacao_cancelamento date
	data_encerramento_efetivo date
	solicitante_cancelamento varchar(30)
	motivo_cancelamento text
}

Table mensalidades {
	mensalidade_id bigint [ pk, increment, not null ]
	contrato_id bigint [ not null ]
	competencia date [ not null ]
	valor decimal(10,2) [ not null ]
	vencimento date [ not null ]
	status varchar(30) [ not null ]
	data_pagamento date

	indexes {
		(contrato_id, competencia) [ unique ]
	}
}

Table rotas {
	rota_id bigint [ pk, increment, not null ]
	motorista_id bigint [ not null ]
	veiculo_id bigint [ not null ]
	escola_id bigint [ not null ]
	data_rota date [ not null ]
	tipo varchar(30) [ not null ]
	status varchar(30) [ not null ]
	horario_inicio time
	horario_fim time
	tempo_estimado_minutos integer
	distancia_km decimal(10,2)
	otimizada boolean [ not null ]
	polyline text [ note: 'tracado devolvido pelo Google; nulo no plano B' ]
}

Table paradas_rota {
	parada_id bigint [ pk, increment, not null ]
	rota_id bigint [ not null ]
	aluno_id bigint
	endereco_id bigint [ not null ]
	ordem integer [ not null ]
	horario_previsto time
	horario_realizado time
	tipo_parada varchar(30) [ not null ]

	indexes {
		(rota_id, ordem) [ unique ]
	}
}

Table faltas {
	falta_id bigint [ pk, increment, not null ]
	aluno_id bigint [ not null ]
	data_falta date [ not null ]
	motivo text
	data_registro timestamp [ not null ]

	indexes {
		(aluno_id, data_falta) [ unique ]
	}
}

Ref: usuarios.usuario_id - motoristas.usuario_id
Ref: usuarios.usuario_id - responsaveis.usuario_id
Ref: usuarios.usuario_id - administradores.usuario_id
Ref: enderecos.endereco_id < responsaveis.endereco_id
Ref: enderecos.endereco_id < escolas.endereco_id
Ref: escolas.escola_id < alunos.escola_id
Ref: enderecos.endereco_id < motoristas.endereco_partida_id
Ref: escolas.escola_id < rotas.escola_id
Ref: alunos.aluno_id < responsaveis_alunos.aluno_id
Ref: responsaveis.usuario_id < responsaveis_alunos.responsavel_id
Ref: alunos.aluno_id < alunos_enderecos.aluno_id
Ref: enderecos.endereco_id < alunos_enderecos.endereco_id
Ref: motoristas.usuario_id < veiculos.motorista_id
Ref: motoristas.usuario_id < motoristas_escolas.motorista_id
Ref: escolas.escola_id < motoristas_escolas.escola_id
Ref: motoristas.usuario_id < documentos.motorista_id
Ref: veiculos.veiculo_id < documentos.veiculo_id
Ref: administradores.usuario_id < validacoes.administrador_id
Ref: motoristas.usuario_id < validacoes.motorista_id
Ref: responsaveis.usuario_id < contratos.responsavel_id
Ref: motoristas.usuario_id < contratos.motorista_id
Ref: alunos.aluno_id < contratos.aluno_id
Ref: contratos.contrato_id < mensalidades.contrato_id
Ref: motoristas.usuario_id < rotas.motorista_id
Ref: veiculos.veiculo_id < rotas.veiculo_id
Ref: rotas.rota_id < paradas_rota.rota_id
Ref: alunos.aluno_id < paradas_rota.aluno_id
Ref: enderecos.endereco_id < paradas_rota.endereco_id
Ref: alunos.aluno_id < faltas.aluno_id
```

### Enums (valores aceitos nas colunas `varchar(30)`)

| Enum | Valores | Origem |
|---|---|---|
| `TipoUsuario` | RESPONSAVEL, MOTORISTA, ADMINISTRADOR | Documentação |
| `StatusConta` | PENDENTE, ATIVA, BLOQUEADA | Documentação |
| `StatusMotorista` | PENDENTE, EM_ANALISE, VALIDADO, REJEITADO | Documentação |
| `TipoVeiculo` | VAN, MICRO_ONIBUS, CARRO | Documentação |
| `TipoDocumento` | CNH, COMPROVANTE_RESIDENCIA, FICHA_CRIMINAL, VISTORIA_VEICULO | Documentação |
| `StatusDocumento` | EM_ANALISE, APROVADO, REJEITADO, VENCIDO | Documentação |
| `StatusContrato` | PENDENTE, ATIVO, RECUSADO, ENCERRADO | Documentação |
| `StatusMensalidade` | PENDENTE, PAGA, ATRASADA | Documentação |
| `TipoRota` | IDA, VOLTA | **Proposto — confirmar** |
| `StatusRota` | PLANEJADA, EM_ANDAMENTO, CONCLUIDA, CANCELADA | **Proposto — confirmar** |
| `TipoParada` | EMBARQUE, DESEMBARQUE | **Proposto — confirmar** |
| `TipoEndereco` | RESIDENCIAL, ALTERNATIVO | **Proposto — confirmar** |
| `Parentesco` | PAI, MAE, AVO, TIO, RESPONSAVEL_LEGAL, OUTRO | **Proposto — confirmar** |
| `StatusVinculo` | ATIVO, INATIVO | **Proposto — confirmar** |

### Entidades JPA ↔ tabelas

| Classe | Tabela |
|---|---|
| `Usuario` (abstrata na prática, herança JOINED) | `usuarios` |
| `Motorista`, `Responsavel`, `Administrador` | `motoristas`, `responsaveis`, `administradores` |
| `Endereco`, `Escola`, `Aluno`, `Veiculo` | `enderecos`, `escolas`, `alunos`, `veiculos` |
| `AlunoEndereco` (chave composta) | `alunos_enderecos` |
| `ResponsavelAluno` (chave composta) | `responsaveis_alunos` |
| `Motorista.escolasAtendidas` (`@ManyToMany`) | `motoristas_escolas` |
| `Documento`, `Validacao` | `documentos`, `validacoes` |
| `Contrato`, `Mensalidade` | `contratos`, `mensalidades` |
| `Rota`, `ParadaRota` | `rotas`, `paradas_rota` |
| `Falta` | `faltas` |

---

## 7. Telas (wireframes da documentação)

T01 Login · T02 Criar conta (escolhe Responsável/Motorista) · T03 Recuperar senha · T04 Alterar senha · T07 Busca de motorista · T08 Perfil do motorista · T10 Detalhe do contrato · T11 Calendário de faltas · T12 Início do motorista · T13 Cadastro de veículo · T14 Envio de documentos · T15 Propostas recebidas · T16 Rota do dia · T17 Cobranças · T18 Fila de validação (admin) · T19 Análise de motorista (admin) · EST Estados de lista (carregando / vazio / erro).

---

## 8. Estado atual (26/10/2026)

### Backend
- ✅ `POST /usuarios` — cadastro com senha em BCrypt, criando a subclasse certa (Motorista/Responsavel/Administrador).
- ✅ Todas as entidades JPA mapeadas conforme o schema.
- ❌ Repositories só para `Usuario`.
- ❌ Integração com o Google Maps (rota do dia): desfeita em 26/10 para ser refeita passo a passo.
- ❌ Sem autenticação/login (só `spring-security-crypto`, sem Spring Security completo / JWT).
- ❌ Sem validação de entrada (`@Valid`) e sem tratamento global de erros (e-mail/CPF duplicado vira 500).
- ❌ Sem endpoints de cadastro de endereço, escola, aluno, veículo e contrato.
- ❌ Schema gerado pelo Hibernate (`ddl-auto: update`); sem Flyway.

### Frontend
- ✅ Telas de busca, perfil do motorista e notificações — **com dados mockados**.
- ❌ Sem login/cadastro, sem integração com a API.
- ⚠️ Bug em `busca.tsx`: o bloco `else` usa `valueField="value"` mas os dados têm `id`; filtros não filtram a lista.
- ⚠️ Arquivo de template sobrando: `components/app-tabs.tsx` (aponta para rota `explore` inexistente).
- ⚠️ Abas atuais (Início/Busca/Notificações) diferem do wireframe (Início/Buscar/Contratos/Perfil).

### Pendências conhecidas
- `POST /usuarios` permite criar ADMINISTRADOR publicamente — bloquear quando houver autenticação.
- Senha padrão do banco commitada em `application.yml`.
- "Avaliações de motorista" aparece no README/introdução, mas não existe no modelo — remover do escopo ou modelar.
- Notificações: decidir se terão tabela própria ou serão só push.
- Diagrama de classes diz Responsável–Aluno 1:N; o schema usa N:N (`responsaveis_alunos`). Atualizar o diagrama.

---

## 9. Plano de próximos passos

1. **Base:** repositories, `@RestControllerAdvice` para erros, `@Valid` nos DTOs, (opcional) Flyway com `ddl-auto: validate`.
2. **Autenticação (REQ01):** Spring Security + JWT, login, telas T01/T02 no app.
3. **Fluxo principal (REQ02):** cadastro de aluno/endereço/escola; veículo, escolas atendidas e documentos do motorista; validação pelo admin; busca; proposta/contrato.
4. **Mensalidades, cancelamento e faltas** (com `@Scheduled`).
5. **Rota otimizada e localização em tempo real** (API de mapas) — por último.

### Decisões em aberto
1. Microserviços × monolito modular.
2. Flyway × Hibernate gerando o schema.
3. Status inicial da conta: responsável já `ATIVA` e só motorista passa por validação?
4. Divisão do trabalho na equipe (por domínio ponta a ponta × back/front).
5. Prazo da próxima entrega.
6. Se a viagem atende mais de uma escola (define Routes API × Route Optimization API), ponto de partida da van e horários/turnos das escolas.

### Decisões tomadas
- **21/10/2026 — API de mapas: Google Maps Platform.** Routes API (`computeRoutes` com `optimizeWaypointOrder`) para a rota do dia e Geocoding API para converter endereço em lat/long no cadastro. Se a viagem atender mais de uma escola, usar a Route Optimization API (pares buscar → deixar). Alternativas avaliadas: OpenRouteService, Mapbox, OSRM + VROOM auto-hospedados. Custos e cuidados: seção 10.

---

## 10. API de mapas (Google) — custos e cuidados

Desde março/2025 o Google não dá mais o crédito de US$ 200; cada SKU tem uma **cota gratuita mensal** e cobra por requisição acima dela (preços em USD, outubro/2026):

| Uso no projeto | SKU | Grátis por mês | Depois (por 1.000) |
|---|---|---|---|
| Endereço → lat/long (cadastro) | Geocoding (Essentials) | 10.000 | US$ 5,00 |
| Rota com ordem otimizada | Compute Routes (Pro) | 5.000 | US$ 10,00 |
| Rota com mais de uma escola | Route Optimization — Single Vehicle (Pro) | 5.000 | US$ 10,00 |
| Mapa no app (SDK mobile) | Maps SDK (Essentials) | ilimitado | — |

Estimativa: 1 chamada de rota por viagem → 10 motoristas × 2 viagens × 22 dias = 440/mês. Grátis até ~110 motoristas.

Cuidados obrigatórios:
- Chave só no backend, em variável de ambiente (`GOOGLE_MAPS_API_KEY`); nunca no Git nem no app. Restringir a chave às APIs usadas.
- Limite diário de requisições por API no Google Cloud Console (o alerta de orçamento só avisa, não bloqueia).
- Testes automatizados nunca chamam o Google (implementação falsa da interface de mapas).
- Geocodificar uma vez por endereço; recalcular a ordem da rota só quando contratos mudarem.
- Termos do Google: coordenadas só podem ficar em cache por tempo limitado; o `place_id` pode ser guardado.
