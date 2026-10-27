# Helpers de teste (`br.com.smartvan.suporte`)

Criados pelo agente `base`. Use estes helpers em vez de montar contexto, JWT e dados na mão. **Não edite** estes
arquivos no seu módulo (outros agentes trabalham em paralelo): se faltar algo, crie uma classe de apoio no seu próprio
pacote de teste e avise no relatório.

## Esqueleto de um teste

```java
package br.com.smartvan.contratacao;

class ContratoIntegracaoTest extends TesteIntegracaoBase {

    @Test
    void responsavelPropoeContrato() throws Exception {
        Responsavel r = fabrica.responsavel();
        Motorista m = fabrica.motorista();                       // VALIDADO
        Escola escola = fabrica.escola();
        fabrica.veiculo(m, 15);
        Aluno aluno = fabrica.aluno(r, escola);

        fazPost("/contratos", Map.of("motoristaId", m.getId(), "alunoId", aluno.getId(), "valorMensal", 350), r)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        assertThat(notificacoesDe(m, TipoNotificacao.PROPOSTA_RECEBIDA)).hasSize(1);
    }
}
```

`TesteIntegracaoBase` já traz `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")` (H2 em modo
PostgreSQL, Flyway real, agendadores desligados) e `@Import(RelogioFixoConfig, MapsFalsoConfig)`.
**Antes de cada teste** ela: apaga todas as linhas de todas as tabelas (inclusive `rotas`, `paradas_rota`, `faltas`,
`mensalidades`, `contratos`, `notificacoes`, `auditorias`, `documentos`, `validacoes`...), reinicia o relógio e o
`FakeMapsProvider`. Os testes **não** são `@Transactional`: cada chamada de repository/serviço commita de verdade, como em
produção (por isso lazy loading fora de transação precisa de `join fetch` ou `@Transactional` no seu teste/serviço).
Cada módulo deve evitar fixtures estáticas compartilhadas entre testes.

Campos `protected` herdados: `mvc` (MockMvc), `fabrica`, `tokens`, `relogio`, `maps`, `json` (`tools.jackson` JsonMapper),
`notificacaoRepository`, `auditoriaRepository`.

## Chamadas HTTP

O último argumento é o usuário logado (`null` = sem token). O token é gerado na hora.

```java
fazGet("/contratos?status=ATIVO", motorista)
fazPost("/contratos", corpoMapOuRecord, responsavel)    // corpo é serializado em JSON
fazPost("/contratos/" + id + "/aceitar", motorista)     // POST sem corpo
fazPut("/motorista/escolas", Map.of("escolaIds", List.of(1)), motorista)
fazDelete("/alunos/1/faltas/2026-11-10", responsavel)
```

Todas devolvem `ResultActions` (`.andExpect(status()..., jsonPath(...))`). Para algo diferente, use `mvc.perform(...)`
com `autenticar(get("/x"), usuario)` ou `.header("Authorization", tokens.bearer(usuario))`. Os paths **não** levam `/api`
(o context-path não vale no MockMvc). Erros vêm no formato do contrato (`$.codigo`, `$.mensagem`, `$.campos`).

## Tokens

```java
tokens.bearer(usuario)                         // "Bearer eyJ..." para o header Authorization
tokens.para(usuario)                           // só o JWT
tokens.bearer(42L, TipoUsuario.MOTORISTA)      // sem entidade (ex.: usuário inexistente)
```

O JWT é validado contra o relógio de teste: se você avançar o relógio mais de 8 h depois de gerar o token, ele expira
(401). Gere o token depois de ajustar o relógio, ou use `fazGet(...)` que gera a cada chamada.

## Relógio (`RelogioMutavel`, campo `relogio`)

O `Clock` `@Primary` dos testes começa em **2026-11-03 09:00 America/Sao_Paulo** (terça) a cada teste.

```java
relogio.hoje()                                  // LocalDate (2026-11-03)
relogio.avancarDias(8);                         // agora 2026-11-11 09:00
relogio.definirData(LocalDate.of(2026, 12, 11)); // muda o dia, mantém 09:00
relogio.definir(LocalDateTime.of(2026, 12, 11, 23, 30));
relogio.instant();                              // Instant atual do relógio
```

Todo código de produção que usa `LocalDate.now(clock)` enxerga a mudança. Nos testes os agendadores ficam desligados:
chame o método público do service direto depois de ajustar o relógio.

## Fábrica (`fabrica`)

Cria direto pelos repositories, devolve a entidade com `id`. E-mail/CPF/placa são únicos; senha = `Fabrica.SENHA`
(`smartvan123`). As entidades voltam **destacadas** (sem sessão): releia pelo repository quando precisar do estado atual.

| Método | O que cria |
|---|---|
| `responsavel()`, `responsavel(nome)` | Responsavel ATIVA |
| `motorista()` | Motorista **VALIDADO** |
| `motorista(StatusMotorista)`, `motorista(nome, status)` | Motorista com o status de validação dado |
| `endereco()` / `endereco("-23.56", "-46.69")` | Endereço em SP com coordenadas |
| `enderecoSemCoordenadas()` | Endereço sem lat/lng (testar geocodificação / `ENDERECO_SEM_COORDENADAS`) |
| `escola()`, `escola(nome)`, `escola(nome, cidade, lat, lng)` | Escola com endereço e coordenadas (lat/lng `null` = sem) |
| `aluno(responsavel, escola)` | Aluno + endereço residencial principal (com coordenadas) + vínculo ATIVO (`acompanha_rota=true`) |
| `aluno(responsavel, escola, endereco)` | Idem com o endereço à escolha (use `endereco(lat, lng)` para posições conhecidas) |
| `veiculo(motorista, capacidade)` | Veículo VAN com placa única |
| `atenderEscolas(motorista, escola...)` | Acrescenta escolas atendidas (devolve o motorista recarregado) |
| `enderecoPartida(motorista)` | Cria e define o ponto de partida (com coordenadas); devolve o endereço |
| `contrato(resp, mot, aluno, StatusContrato)` | Contrato R$ 350,00, `dataInicio = hoje` (relógio), no status dado |
| `contrato(resp, mot, aluno, status, valor, dataInicio)` | Idem, valor e início à escolha |
| `comCancelamento(contrato, TipoUsuario solicitante, dataSolicitacao, motivo)` | Preenche os 4 campos de cancelamento; `dataEncerramentoEfetivo = solicitação + 30`. O **status não muda** (continua ATIVO) |
| `comCancelamento(contrato, solicitante, dataSolicitacao, dataEncerramentoEfetivo, motivo)` | Idem com data de encerramento explícita (ex.: no passado, para o agendador) |
| `mensalidade(contrato, competencia, StatusMensalidade)` | Competência normalizada p/ dia 1, vencimento dia 10, valor = valor do contrato; `PAGA` já vem com `dataPagamento` |
| `mensalidade(contrato, competencia, vencimento, status, dataPagamento)` | Tudo explícito (ex.: `ATRASADA` com vencimento passado) |
| `falta(aluno, data)` | Falta |

Exemplo de cenário completo de cancelamento + mensalidade:

```java
Contrato c = fabrica.contrato(r, m, aluno, StatusContrato.ATIVO);
fabrica.comCancelamento(c, TipoUsuario.RESPONSAVEL, relogio.hoje().minusDays(30), "mudança");
fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.ATRASADA);   // ou a versão com vencimento explícito
```

Para criar `Rota`/`ParadaRota` na mão (testes do módulo `rota`/`cancelamento`), use `RotaRepository` e
`rota.adicionarParada(parada)` (cascade salva as paradas); `Rota` exige motorista, veículo, escola, data, tipo, status e
`otimizada`.

## MapsProvider falso (`maps`)

`FakeMapsProvider` é o `MapsProvider` `@Primary` dos testes (nunca chama o Google). **Padrão** (restaurado antes de cada
teste):

- `geocodificar` → sempre `FakeMapsProvider.COORDENADA_PADRAO` (-23.5650, -46.6900);
- `otimizarRota` → mantém a ordem recebida (0,1,2...), 300 s entre paradas (`segundosAteCadaParada` = 300, 600, ...),
  `segundosTotal = (n+1)*300`, `metrosTotal = (n+1)*2000`, polyline `"fake-polyline"`.

Para mudar o comportamento num teste:

```java
maps.falharNaOtimizacao();                       // otimizarRota lança MapsIndisponivelException (aciona o plano B)
maps.otimizarComOrdem(2, 0, 1);                  // devolve esta ordem de índices (300 s entre paradas)
maps.otimizarComo((partida, paradas, destino, saida) ->
        new RotaOtimizada(List.of(1, 0), List.of(600, 1200), 1800, 9000, null));   // resultado totalmente à mão
maps.naoGeocodificar();                          // geocodificar devolve Optional.empty()
maps.geocodificarComo(texto -> texto.contains("Perdizes")
        ? Optional.of(new Coordenada(new BigDecimal("-23.53"), new BigDecimal("-46.67")))
        : Optional.empty());
```

Para verificar o que o código chamou: `maps.geocodificacoes` (lista de textos de endereço) e `maps.otimizacoes` (lista de
`ChamadaOtimizacao(partida, paradas, destino, saida)`). Ex.:
`assertThat(maps.otimizacoes).hasSize(1); assertThat(maps.otimizacoes.getFirst().paradas()).hasSize(3);`.

## Verificar notificações e auditorias

```java
notificacoesDe(usuario)                                   // List<Notificacao>, mais recentes primeiro
notificacoesDe(usuario, TipoNotificacao.MENSALIDADE_PAGA) // filtradas por tipo
auditorias("Mensalidade", mensalidadeId)                  // List<Auditoria> (entidade, entidadeId)
```

`Notificacao` tem `getTipo()`, `getTitulo()`, `getMensagem()`, `getReferenciaId()`, `isLida()`. Pela API:
`fazGet("/notificacoes", usuario)`.

## Outros detalhes

- `limparBanco()` (protected) apaga tudo de novo no meio do teste, se precisar.
- Cada novo módulo com tabela nova precisa acrescentar a tabela em `TABELAS_EM_ORDEM_DE_LIMPEZA` (hoje todas as tabelas
  V1/V2 estão lá). Só mexa nisso se criar migration nova, e avise o orquestrador (o arquivo é compartilhado).
- Perfil `dev` em teste (seed): `@ActiveProfiles({"test", "dev"})`, veja `DadosDemonstracaoTest`.
- `SuporteTest` testa estes helpers; rode-o se alterar algo aqui.
