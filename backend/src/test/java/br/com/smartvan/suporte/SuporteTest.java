package br.com.smartvan.suporte;

import br.com.smartvan.comum.maps.Coordenada;
import br.com.smartvan.comum.maps.MapsIndisponivelException;
import br.com.smartvan.enums.*;
import br.com.smartvan.model.*;
import br.com.smartvan.notificacao.NotificacaoService;
import br.com.smartvan.notificacao.TipoNotificacao;
import br.com.smartvan.auditoria.AuditoriaService;
import br.com.smartvan.repositorio.RotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa os próprios helpers (para os outros agentes poderem confiar neles). */
class SuporteTest extends TesteIntegracaoBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired RotaRepository rotaRepository;
    @Autowired NotificacaoService notificacaoService;
    @Autowired AuditoriaService auditoriaService;

    @Test
    void relogioComecaEm3DeNovembroDe2026EAvanca() {
        assertThat(clock).isSameAs(relogio);
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 11, 3));
        relogio.avancarDias(8);
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 11, 11));
        relogio.definirData(LocalDate.of(2026, 12, 25));
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 12, 25));
        relogio.reiniciar();
        assertThat(relogio.hoje()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void fabricaCriaContratoEmQualquerStatusComCancelamentoEMensalidade() {
        Responsavel r = fabrica.responsavel();
        Motorista m = fabrica.motorista(StatusMotorista.VALIDADO);
        Escola escola = fabrica.escola();
        Aluno aluno = fabrica.aluno(r, escola);
        fabrica.veiculo(m, 15);
        fabrica.atenderEscolas(m, escola);
        fabrica.enderecoPartida(m);

        for (StatusContrato s : StatusContrato.values()) {
            Contrato c = fabrica.contrato(r, m, fabrica.aluno(r, escola), s);
            assertThat(c.getId()).isNotNull();
            assertThat(c.getStatus()).isEqualTo(s);
            assertThat(c.getDataInicio()).isEqualTo(relogio.hoje());
        }

        Contrato ativo = fabrica.contrato(r, m, aluno, StatusContrato.ATIVO);
        Contrato cancel = fabrica.comCancelamento(ativo, TipoUsuario.RESPONSAVEL, relogio.hoje(), "mudança de escola");
        assertThat(cancel.getDataEncerramentoEfetivo()).isEqualTo(relogio.hoje().plusDays(30));
        assertThat(cancel.getStatus()).isEqualTo(StatusContrato.ATIVO);
        assertThat(cancel.getSolicitanteCancelamento()).isEqualTo(TipoUsuario.RESPONSAVEL);

        Mensalidade pendente = fabrica.mensalidade(ativo, LocalDate.of(2026, 11, 15), StatusMensalidade.PENDENTE);
        Mensalidade paga = fabrica.mensalidade(ativo, LocalDate.of(2026, 10, 1), StatusMensalidade.PAGA);
        Mensalidade atrasada = fabrica.mensalidade(ativo, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10),
                StatusMensalidade.ATRASADA, null);
        assertThat(pendente.getCompetencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(pendente.getVencimento()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(paga.getDataPagamento()).isNotNull();
        assertThat(atrasada.getStatus()).isEqualTo(StatusMensalidade.ATRASADA);
        assertThat(pendente.getValor()).isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    void tokenGeradoFuncionaNaApi() throws Exception {
        Responsavel r = fabrica.responsavel();
        fazGet("/auth/me", r).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(r.getId()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/me")
                        .header("Authorization", tokens.bearer(r)))
                .andExpect(status().isOk());
    }

    @Test
    void mapsFalsoPadraoOrdenaEmOrdemERegistraChamadas() {
        Coordenada p = new Coordenada(BigDecimal.ONE, BigDecimal.ONE);
        var resultado = maps.otimizarRota(p, List.of(p, p, p), p, Instant.now());
        assertThat(resultado.ordemParadas()).containsExactly(0, 1, 2);
        assertThat(resultado.segundosAteCadaParada()).containsExactly(300, 600, 900);
        assertThat(maps.otimizacoes).hasSize(1);
        assertThat(maps.geocodificar("Rua X")).contains(FakeMapsProvider.COORDENADA_PADRAO);
        assertThat(maps.geocodificacoes).containsExactly("Rua X");
    }

    @Test
    void mapsFalsoPodeLancarExcecaoOuDevolverOrdemConhecida() {
        Coordenada p = new Coordenada(BigDecimal.ONE, BigDecimal.ONE);
        maps.falharNaOtimizacao();
        assertThatThrownBy(() -> maps.otimizarRota(p, List.of(p), p, Instant.now()))
                .isInstanceOf(MapsIndisponivelException.class);

        maps.otimizarComOrdem(2, 0, 1);
        assertThat(maps.otimizarRota(p, List.of(p, p, p), p, Instant.now()).ordemParadas()).containsExactly(2, 0, 1);

        maps.naoGeocodificar();
        assertThat(maps.geocodificar("x")).isEqualTo(Optional.empty());

        maps.reiniciar();
        assertThat(maps.geocodificar("x")).isPresent();
        assertThat(maps.geocodificacoes).hasSize(1);
    }

    @Test
    void notificacoesEAuditoriasCriadasPorServicosPodemSerVerificadas() {
        Responsavel r = fabrica.responsavel();
        notificacaoService.notificar(r.getId(), TipoNotificacao.ROTA_GERADA, "Rota", "Sua rota saiu", 7L);
        notificacaoService.notificar(r.getId(), TipoNotificacao.MENSALIDADE_GERADA, "Mensalidade", "Gerada", 8L);
        auditoriaService.registrar(r.getId(), "ACAO", "Mensalidade", 8L, "x");

        assertThat(notificacoesDe(r)).hasSize(2);
        assertThat(notificacoesDe(r, TipoNotificacao.ROTA_GERADA)).singleElement()
                .satisfies(n -> assertThat(n.getReferenciaId()).isEqualTo(7L));
        assertThat(notificacoesDe(fabrica.responsavel())).isEmpty();
        assertThat(auditorias("Mensalidade", 8L)).hasSize(1);
    }

    @Test
    void limpezaApagaTodasAsTabelasDosOutrosModulos() {
        Responsavel r = fabrica.responsavel();
        Motorista m = fabrica.motorista();
        Escola escola = fabrica.escola();
        Aluno aluno = fabrica.aluno(r, escola);
        Veiculo v = fabrica.veiculo(m, 15);
        fabrica.atenderEscolas(m, escola);
        fabrica.enderecoPartida(m);
        Contrato c = fabrica.contrato(r, m, aluno, StatusContrato.ATIVO);
        fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PENDENTE);
        fabrica.falta(aluno, relogio.hoje().plusDays(1));
        notificacaoService.notificar(r.getId(), TipoNotificacao.ROTA_GERADA, "t", "m", null);
        auditoriaService.registrar(r.getId(), "A", "E", 1L, null);

        Rota rota = new Rota();
        rota.setMotorista(m);
        rota.setVeiculo(v);
        rota.setEscola(escola);
        rota.setDataRota(relogio.hoje());
        rota.setTipo(TipoRota.IDA);
        rota.setStatus(StatusRota.PLANEJADA);
        rota.setOtimizada(false);
        ParadaRota parada = new ParadaRota();
        parada.setAluno(aluno);
        parada.setEndereco(escola.getEndereco());
        parada.setOrdem(1);
        parada.setTipoParada(TipoParada.EMBARQUE);
        rota.adicionarParada(parada);
        rotaRepository.save(rota);

        for (String tabela : List.of("paradas_rota", "rotas", "faltas", "mensalidades", "contratos", "notificacoes",
                "auditorias", "alunos", "usuarios", "escolas", "enderecos")) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela, Integer.class))
                    .as(tabela).isPositive();
        }

        limparBanco();

        for (String tabela : List.of("paradas_rota", "rotas", "faltas", "mensalidades", "contratos", "notificacoes",
                "auditorias", "alunos", "alunos_enderecos", "responsaveis_alunos", "veiculos", "motoristas_escolas",
                "motoristas", "responsaveis", "usuarios", "escolas", "enderecos")) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela, Integer.class))
                    .as(tabela).isZero();
        }
    }
}
