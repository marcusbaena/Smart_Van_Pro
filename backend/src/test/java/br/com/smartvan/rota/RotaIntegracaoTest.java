package br.com.smartvan.rota;

import br.com.smartvan.comum.maps.RotaOtimizada;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.Escola;
import br.com.smartvan.model.Motorista;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.notificacao.Notificacao;
import br.com.smartvan.notificacao.TipoNotificacao;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RotaIntegracaoTest extends TesteIntegracaoBase {

    private Motorista motorista;
    private Escola escola;
    private Responsavel resp1;
    private Responsavel resp2;
    /** Ordenados por nome (mesma ordem enviada ao MapsProvider). */
    private List<Aluno> alunos;
    private LocalDate amanha;

    @BeforeEach
    void cenario() {
        amanha = relogio.hoje().plusDays(1);
        motorista = fabrica.motorista();
        escola = fabrica.escola();
        fabrica.atenderEscolas(motorista, escola);
        fabrica.enderecoPartida(motorista);
        fabrica.veiculo(motorista, 15);
        resp1 = fabrica.responsavel();
        resp2 = fabrica.responsavel();
        Aluno a1 = fabrica.aluno(resp1, escola);
        Aluno a2 = fabrica.aluno(resp2, escola);
        fabrica.contrato(resp1, motorista, a1, StatusContrato.ATIVO);
        fabrica.contrato(resp2, motorista, a2, StatusContrato.ATIVO);
        alunos = new java.util.ArrayList<>(List.of(a1, a2));
        alunos.sort(Comparator.comparing(Aluno::getNome).thenComparing(Aluno::getId));
    }

    private Map<String, Object> corpo(String tipo) {
        Map<String, Object> m = new HashMap<>();
        m.put("data", amanha.toString());
        m.put("tipo", tipo);
        m.put("escolaId", escola.getId());
        return m;
    }

    private ResultActions gerar(Map<String, Object> corpo) throws Exception {
        return fazPost("/motorista/rotas/gerar", corpo, motorista);
    }

    // ------------------------------------------------------------ IDA / VOLTA

    @Test
    void geraRotaDeIdaOtimizada() throws Exception {
        gerar(corpo("IDA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANEJADA"))
                .andExpect(jsonPath("$.tipo").value("IDA"))
                .andExpect(jsonPath("$.data").value(amanha.toString()))
                .andExpect(jsonPath("$.otimizada").value(true))
                .andExpect(jsonPath("$.polyline").value("fake-polyline"))
                .andExpect(jsonPath("$.escola.id").value(escola.getId()))
                .andExpect(jsonPath("$.veiculo.placa").isString())
                .andExpect(jsonPath("$.horarioInicio").value("06:30"))
                .andExpect(jsonPath("$.horarioFim").value("06:45"))
                .andExpect(jsonPath("$.tempoEstimadoMinutos").value(15))
                .andExpect(jsonPath("$.distanciaKm").value(6.0))
                .andExpect(jsonPath("$.paradas.length()").value(3))
                .andExpect(jsonPath("$.paradas[0].ordem").value(1))
                .andExpect(jsonPath("$.paradas[0].tipoParada").value("EMBARQUE"))
                .andExpect(jsonPath("$.paradas[0].aluno.id").value(alunos.get(0).getId()))
                .andExpect(jsonPath("$.paradas[0].horarioPrevisto").value("06:35"))
                .andExpect(jsonPath("$.paradas[1].tipoParada").value("EMBARQUE"))
                .andExpect(jsonPath("$.paradas[1].aluno.id").value(alunos.get(1).getId()))
                .andExpect(jsonPath("$.paradas[1].horarioPrevisto").value("06:40"))
                .andExpect(jsonPath("$.paradas[2].ordem").value(3))
                .andExpect(jsonPath("$.paradas[2].tipoParada").value("DESEMBARQUE"))
                .andExpect(jsonPath("$.paradas[2].aluno").doesNotExist())
                .andExpect(jsonPath("$.paradas[2].endereco.id").value(escola.getEndereco().getId()))
                .andExpect(jsonPath("$.paradas[2].horarioPrevisto").value("06:45"));

        assertThat(maps.otimizacoes).hasSize(1);
        assertThat(maps.otimizacoes.getFirst().paradas()).hasSize(2);
    }

    @Test
    void geraRotaDeVoltaSaindoDaEscola() throws Exception {
        gerar(corpo("VOLTA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("VOLTA"))
                .andExpect(jsonPath("$.horarioInicio").value("12:30"))
                .andExpect(jsonPath("$.horarioFim").value("12:45"))
                .andExpect(jsonPath("$.paradas.length()").value(3))
                .andExpect(jsonPath("$.paradas[0].tipoParada").value("EMBARQUE"))
                .andExpect(jsonPath("$.paradas[0].aluno").doesNotExist())
                .andExpect(jsonPath("$.paradas[0].endereco.id").value(escola.getEndereco().getId()))
                .andExpect(jsonPath("$.paradas[0].horarioPrevisto").value("12:30"))
                .andExpect(jsonPath("$.paradas[1].tipoParada").value("DESEMBARQUE"))
                .andExpect(jsonPath("$.paradas[1].aluno.id").value(alunos.get(0).getId()))
                .andExpect(jsonPath("$.paradas[1].horarioPrevisto").value("12:35"))
                .andExpect(jsonPath("$.paradas[2].tipoParada").value("DESEMBARQUE"))
                .andExpect(jsonPath("$.paradas[2].aluno.id").value(alunos.get(1).getId()))
                .andExpect(jsonPath("$.paradas[2].horarioPrevisto").value("12:40"));

        // VOLTA: parte da escola e termina no ponto de partida do motorista
        var chamada = maps.otimizacoes.getFirst();
        assertThat(chamada.partida().latitude()).isEqualByComparingTo("-23.56");
        assertThat(chamada.destino().latitude()).isEqualByComparingTo("-23.563");
    }

    @Test
    void horarioDeSaidaInformadoAcumulaTempo() throws Exception {
        Map<String, Object> c = corpo("IDA");
        c.put("horarioSaida", "07:10");

        gerar(c)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horarioInicio").value("07:10"))
                .andExpect(jsonPath("$.paradas[0].horarioPrevisto").value("07:15"))
                .andExpect(jsonPath("$.paradas[1].horarioPrevisto").value("07:20"))
                .andExpect(jsonPath("$.paradas[2].horarioPrevisto").value("07:25"));
    }

    @Test
    void usaAOrdemDevolvidaPeloProvider() throws Exception {
        maps.otimizarComOrdem(1, 0);

        gerar(corpo("IDA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otimizada").value(true))
                .andExpect(jsonPath("$.paradas[0].aluno.id").value(alunos.get(1).getId()))
                .andExpect(jsonPath("$.paradas[1].aluno.id").value(alunos.get(0).getId()));
    }

    @Test
    void usaTemposEPolylineDoProvider() throws Exception {
        maps.otimizarComo((partida, paradas, destino, saida) ->
                new RotaOtimizada(List.of(1, 0), List.of(600, 1200), 1800, 9000, "abc123"));

        gerar(corpo("IDA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.polyline").value("abc123"))
                .andExpect(jsonPath("$.tempoEstimadoMinutos").value(30))
                .andExpect(jsonPath("$.distanciaKm").value(9.0))
                .andExpect(jsonPath("$.horarioFim").value("07:00"))
                .andExpect(jsonPath("$.paradas[0].horarioPrevisto").value("06:40"))
                .andExpect(jsonPath("$.paradas[1].horarioPrevisto").value("06:50"));
    }

    // ------------------------------------------------------------ plano B

    @Test
    void planoBQuandoProviderFalhaOrdenaPorProximidade() throws Exception {
        // limpa o cenario e monta posicoes conhecidas: partida em (-23.563, -46.697)
        limparBanco();
        Motorista m = fabrica.motorista();
        Escola e = fabrica.escola();
        fabrica.atenderEscolas(m, e);
        fabrica.enderecoPartida(m);
        fabrica.veiculo(m, 15);
        Responsavel r = fabrica.responsavel();
        Aluno longe = fabrica.aluno(r, e, fabrica.endereco("-23.6000000", "-46.8000000"));
        Aluno perto = fabrica.aluno(r, e, fabrica.endereco("-23.5650000", "-46.6990000"));
        fabrica.contrato(r, m, longe, StatusContrato.ATIVO);
        fabrica.contrato(r, m, perto, StatusContrato.ATIVO);
        maps.falharNaOtimizacao();

        Map<String, Object> c = new HashMap<>();
        c.put("data", amanha.toString());
        c.put("tipo", "IDA");
        c.put("escolaId", e.getId());
        fazPost("/motorista/rotas/gerar", c, m)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otimizada").value(false))
                .andExpect(jsonPath("$.polyline").doesNotExist())
                .andExpect(jsonPath("$.tempoEstimadoMinutos").isNumber())
                .andExpect(jsonPath("$.distanciaKm").isNumber())
                .andExpect(jsonPath("$.paradas[0].aluno.id").value(perto.getId()))
                .andExpect(jsonPath("$.paradas[1].aluno.id").value(longe.getId()))
                .andExpect(jsonPath("$.paradas[2].tipoParada").value("DESEMBARQUE"))
                .andExpect(jsonPath("$.paradas[2].aluno").doesNotExist());
    }

    @Test
    void planoBQuandoProviderDevolveOrdemInvalida() throws Exception {
        maps.otimizarComo((p, paradas, d, s) -> new RotaOtimizada(List.of(0, 0), List.of(1, 2), 3, 4, "x"));

        gerar(corpo("IDA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otimizada").value(false))
                .andExpect(jsonPath("$.polyline").doesNotExist());
    }

    // ------------------------------------------------------------ alunos

    @Test
    void alunoComFaltaFicaForaDaRota() throws Exception {
        fabrica.falta(alunos.get(0), amanha);

        gerar(corpo("IDA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paradas.length()").value(2))
                .andExpect(jsonPath("$.paradas[0].aluno.id").value(alunos.get(1).getId()));
    }

    @Test
    void faltaEmOutroDiaNaoTiraOAluno() throws Exception {
        fabrica.falta(alunos.get(0), amanha.plusDays(1));

        gerar(corpo("IDA")).andExpect(status().isCreated()).andExpect(jsonPath("$.paradas.length()").value(3));
    }

    @Test
    void contratoNaoAtivoOuOutraEscolaNaoEntra() throws Exception {
        Aluno pendente = fabrica.aluno(resp1, escola);
        fabrica.contrato(resp1, motorista, pendente, StatusContrato.PENDENTE);
        Aluno outraEscola = fabrica.aluno(resp1, fabrica.escola());
        fabrica.contrato(resp1, motorista, outraEscola, StatusContrato.ATIVO);
        Aluno deOutroMotorista = fabrica.aluno(resp1, escola);
        fabrica.contrato(resp1, fabrica.motorista(), deOutroMotorista, StatusContrato.ATIVO);

        gerar(corpo("IDA")).andExpect(status().isCreated()).andExpect(jsonPath("$.paradas.length()").value(3));
    }

    @Test
    void semAlunosDa422() throws Exception {
        fabrica.falta(alunos.get(0), amanha);
        fabrica.falta(alunos.get(1), amanha);

        gerar(corpo("IDA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ROTA_SEM_ALUNOS"));
    }

    @Test
    void escolaSemContratosDa422() throws Exception {
        Map<String, Object> c = corpo("IDA");
        c.put("escolaId", fabrica.escola().getId());

        gerar(c).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("ROTA_SEM_ALUNOS"));
    }

    @Test
    void escolaInexistenteDa404() throws Exception {
        Map<String, Object> c = corpo("IDA");
        c.put("escolaId", 999999);

        gerar(c).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ pre-condicoes

    @Test
    void semEnderecoDePartidaDa422() throws Exception {
        Motorista sem = fabrica.motorista();
        fabrica.veiculo(sem, 10);
        Aluno a = fabrica.aluno(resp1, escola);
        fabrica.contrato(resp1, sem, a, StatusContrato.ATIVO);

        fazPost("/motorista/rotas/gerar", corpo("IDA"), sem)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ENDERECO_PARTIDA_OBRIGATORIO"));
    }

    @Test
    void enderecoDeAlunoSemCoordenadasQueNaoGeocodificaDa422() throws Exception {
        Aluno a = fabrica.aluno(resp1, escola, fabrica.enderecoSemCoordenadas());
        fabrica.contrato(resp1, motorista, a, StatusContrato.ATIVO);
        maps.naoGeocodificar();

        gerar(corpo("IDA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ENDERECO_SEM_COORDENADAS"));
    }

    @Test
    void enderecoSemCoordenadasQueGeocodificaEUsado() throws Exception {
        Aluno a = fabrica.aluno(resp1, escola, fabrica.enderecoSemCoordenadas());
        fabrica.contrato(resp1, motorista, a, StatusContrato.ATIVO);

        gerar(corpo("IDA")).andExpect(status().isCreated()).andExpect(jsonPath("$.paradas.length()").value(4));
        assertThat(maps.geocodificacoes).hasSize(1);
    }

    @Test
    void dataPassadaDa422() throws Exception {
        Map<String, Object> c = corpo("IDA");
        c.put("data", relogio.hoje().minusDays(1).toString());

        gerar(c).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("DATA_PASSADA"));
    }

    @Test
    void corpoInvalidoDa400() throws Exception {
        gerar(Map.of("data", amanha.toString())).andExpect(status().isBadRequest());
    }

    @Test
    void responsavelNaoGeraRota() throws Exception {
        fazPost("/motorista/rotas/gerar", corpo("IDA"), resp1).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ veiculo

    @Test
    void semVeiculoDa422() throws Exception {
        Motorista sem = fabrica.motorista();
        fabrica.enderecoPartida(sem);
        Aluno a = fabrica.aluno(resp1, escola);
        fabrica.contrato(resp1, sem, a, StatusContrato.ATIVO);

        fazPost("/motorista/rotas/gerar", corpo("IDA"), sem)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("VEICULO_OBRIGATORIO"));
    }

    @Test
    void variosVeiculosSemEscolherDa422EComIdFunciona() throws Exception {
        var segundo = fabrica.veiculo(motorista, 12);

        gerar(corpo("IDA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("VEICULO_AMBIGUO"));

        Map<String, Object> c = corpo("IDA");
        c.put("veiculoId", segundo.getId());
        gerar(c).andExpect(status().isCreated()).andExpect(jsonPath("$.veiculo.id").value(segundo.getId()));
    }

    @Test
    void veiculoDeOutroMotoristaDa403() throws Exception {
        var alheio = fabrica.veiculo(fabrica.motorista(), 10);
        Map<String, Object> c = corpo("IDA");
        c.put("veiculoId", alheio.getId());

        gerar(c).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    // ------------------------------------------------------------ reaproveitamento

    @Test
    void reaproveitaRotaComMesmoConjuntoDeAlunos() throws Exception {
        String id = json.readTree(gerar(corpo("IDA")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("id").asString();

        gerar(corpo("IDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(id)));
        assertThat(maps.otimizacoes).hasSize(1);
        fazGet("/motorista/rotas?data=" + amanha, motorista).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void geraNovamenteQuandoSurgeFaltaNova() throws Exception {
        long idAntigo = json.readTree(gerar(corpo("IDA")).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        fabrica.falta(alunos.get(0), amanha);

        String resposta = gerar(corpo("IDA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paradas.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(resposta).get("id").asLong()).isNotEqualTo(idAntigo);
        fazGet("/motorista/rotas?data=" + amanha, motorista).andExpect(jsonPath("$.length()").value(1));
        fazGet("/motorista/rotas/" + idAntigo, motorista).andExpect(status().isNotFound());
    }

    @Test
    void idaEVoltaSaoRotasDiferentes() throws Exception {
        gerar(corpo("IDA")).andExpect(status().isCreated());
        gerar(corpo("VOLTA")).andExpect(status().isCreated());

        fazGet("/motorista/rotas?data=" + amanha, motorista)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("IDA"))
                .andExpect(jsonPath("$[1].tipo").value("VOLTA"));
    }

    // ------------------------------------------------------------ consultas

    @Test
    void listaPadraoEHojeEBuscaPorId() throws Exception {
        fazGet("/motorista/rotas", motorista).andExpect(jsonPath("$.length()").value(0));

        Map<String, Object> c = corpo("IDA");
        c.put("data", relogio.hoje().toString());
        long id = json.readTree(gerar(c).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        fazGet("/motorista/rotas", motorista)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        fazGet("/motorista/rotas/" + id, motorista)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradas.length()").value(3));
    }

    @Test
    void rotaDeOutroMotoristaDa403EInexistenteDa404() throws Exception {
        long id = json.readTree(gerar(corpo("IDA")).andReturn().getResponse().getContentAsString()).get("id").asLong();

        fazGet("/motorista/rotas/" + id, fabrica.motorista()).andExpect(status().isForbidden());
        fazGet("/motorista/rotas/999999", motorista).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ notificacoes

    @Test
    void notificaOsResponsaveisComOHorarioPrevisto() throws Exception {
        long rotaId = json.readTree(gerar(corpo("IDA")).andReturn().getResponse().getContentAsString()).get("id").asLong();

        List<Notificacao> n1 = notificacoesDe(resp1, TipoNotificacao.ROTA_GERADA);
        List<Notificacao> n2 = notificacoesDe(resp2, TipoNotificacao.ROTA_GERADA);
        assertThat(n1).hasSize(1);
        assertThat(n2).hasSize(1);
        assertThat(n1.getFirst().getReferenciaId()).isEqualTo(rotaId);
        // alunos[0] = primeira parada 06:35, alunos[1] = segunda 06:40
        Responsavel donoDoPrimeiro = alunos.get(0).getId().equals(alunoDe(resp1)) ? resp1 : resp2;
        Notificacao doPrimeiro = notificacoesDe(donoDoPrimeiro, TipoNotificacao.ROTA_GERADA).getFirst();
        assertThat(doPrimeiro.getMensagem()).contains("06:35");
    }

    private Long alunoDe(Responsavel r) {
        // o aluno do resp1 e o criado primeiro no cenario (id menor)
        return r.getId().equals(resp1.getId())
                ? alunos.stream().map(Aluno::getId).min(Long::compare).orElseThrow()
                : alunos.stream().map(Aluno::getId).max(Long::compare).orElseThrow();
    }

    @Test
    void alunoComFaltaNaoRecebeNotificacao() throws Exception {
        fabrica.falta(alunos.get(0), amanha);
        gerar(corpo("IDA")).andExpect(status().isCreated());

        int total = notificacoesDe(resp1, TipoNotificacao.ROTA_GERADA).size()
                + notificacoesDe(resp2, TipoNotificacao.ROTA_GERADA).size();
        assertThat(total).isEqualTo(1);
    }
}
