package br.com.smartvan.financeiro;

import br.com.smartvan.comum.evento.ContratoAtivadoEvent;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.StatusMensalidade;
import br.com.smartvan.enums.TipoUsuario;
import br.com.smartvan.financeiro.service.MensalidadeConsultaService;
import br.com.smartvan.financeiro.service.MensalidadeJob;
import br.com.smartvan.model.*;
import br.com.smartvan.notificacao.TipoNotificacao;
import br.com.smartvan.repositorio.MensalidadeRepository;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MensalidadeIntegracaoTest extends TesteIntegracaoBase {

    @Autowired ApplicationEventPublisher publicador;
    @Autowired MensalidadeJob job;
    @Autowired MensalidadeRepository mensalidades;
    @Autowired MensalidadeConsultaService consulta;
    @Autowired TransactionTemplate tx;

    Responsavel resp;
    Motorista mot;
    Escola escola;
    Aluno aluno;

    @BeforeEach
    void cenario() {
        resp = fabrica.responsavel();
        mot = fabrica.motorista();
        escola = fabrica.escola();
        aluno = fabrica.aluno(resp, escola);
    }

    private Contrato ativo() {
        return fabrica.contrato(resp, mot, aluno, StatusContrato.ATIVO);
    }

    private Mensalidade unica() {
        List<Mensalidade> todas = mensalidades.findAll();
        assertThat(todas).hasSize(1);
        return todas.getFirst();
    }

    @Test
    void eventoGeraUmaMensalidadeENotifica() {
        Contrato c = ativo();
        publicador.publishEvent(new ContratoAtivadoEvent(c.getId()));

        Mensalidade m = unica();
        assertThat(m.getCompetencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(m.getStatus()).isEqualTo(StatusMensalidade.PENDENTE);
        assertThat(m.getValor()).isEqualByComparingTo("350.00");
        assertThat(m.getVencimento()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(notificacoesDe(resp, TipoNotificacao.MENSALIDADE_GERADA)).hasSize(1);
    }

    @Test
    void eventoDentroDeTransacaoTambemGera() {
        Contrato c = ativo();
        tx.executeWithoutResult(s -> publicador.publishEvent(new ContratoAtivadoEvent(c.getId())));
        assertThat(mensalidades.count()).isEqualTo(1);
    }

    @Test
    void eventoRepetidoNaoDuplica() {
        Contrato c = ativo();
        publicador.publishEvent(new ContratoAtivadoEvent(c.getId()));
        publicador.publishEvent(new ContratoAtivadoEvent(c.getId()));

        unica();
        assertThat(notificacoesDe(resp, TipoNotificacao.MENSALIDADE_GERADA)).hasSize(1);
    }

    @Test
    void ativacaoDepoisDoDia10VenceEmCincoDias() {
        relogio.definirData(LocalDate.of(2026, 11, 15));
        Contrato c = ativo();
        publicador.publishEvent(new ContratoAtivadoEvent(c.getId()));

        assertThat(unica().getVencimento()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void ativacaoNoDia10AindaVenceNoDia10() {
        relogio.definirData(LocalDate.of(2026, 11, 10));
        Contrato c = ativo();
        publicador.publishEvent(new ContratoAtivadoEvent(c.getId()));

        assertThat(unica().getVencimento()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void jobGeraSoParaAtivoEEhIdempotente() {
        Contrato ativo = ativo();
        Aluno a2 = fabrica.aluno(resp, escola);
        Aluno a3 = fabrica.aluno(resp, escola);
        Aluno a4 = fabrica.aluno(resp, escola);
        fabrica.contrato(resp, mot, a2, StatusContrato.PENDENTE);
        fabrica.contrato(resp, mot, a3, StatusContrato.RECUSADO);
        fabrica.contrato(resp, mot, a4, StatusContrato.ENCERRADO);

        assertThat(job.gerarCompetenciaAtual()).isEqualTo(1);
        assertThat(job.gerarCompetenciaAtual()).isZero();

        assertThat(unica().getContrato().getId()).isEqualTo(ativo.getId());
        assertThat(notificacoesDe(resp, TipoNotificacao.MENSALIDADE_GERADA)).hasSize(1);
    }

    @Test
    void jobEhIdempotenteComMensalidadeJaExistente() {
        Contrato c = ativo();
        fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PENDENTE);

        assertThat(job.gerarCompetenciaAtual()).isZero();
        unica();
    }

    @Test
    void jobGeraNovaCompetenciaNoMesSeguinte() {
        Contrato c = ativo();
        job.gerarCompetenciaAtual();
        relogio.definirData(LocalDate.of(2026, 12, 1));
        job.gerarCompetenciaAtual();

        assertThat(mensalidades.count()).isEqualTo(2);
    }

    @Test
    void contratoComCancelamentoSolicitadoAindaGera() {
        Contrato c = ativo();
        fabrica.comCancelamento(c, TipoUsuario.RESPONSAVEL, relogio.hoje(), "mudança de cidade");

        assertThat(job.gerarCompetenciaAtual()).isEqualTo(1);
        unica();
    }

    @Test
    void marcarAtrasadasSoUmaVez() {
        Contrato c = ativo();
        job.gerarCompetenciaAtual();                  // vencimento 2026-11-10
        relogio.definirData(LocalDate.of(2026, 11, 10));
        assertThat(job.marcarAtrasadas()).isZero();   // vence hoje: ainda não atrasa

        relogio.definirData(LocalDate.of(2026, 11, 11));
        assertThat(job.marcarAtrasadas()).isEqualTo(1);
        assertThat(job.marcarAtrasadas()).isZero();

        assertThat(unica().getStatus()).isEqualTo(StatusMensalidade.ATRASADA);
        assertThat(notificacoesDe(resp, TipoNotificacao.MENSALIDADE_ATRASADA)).hasSize(1);
    }

    @Test
    void marcarAtrasadasIgnoraPagas() {
        Contrato c = ativo();
        fabrica.mensalidade(c, relogio.hoje().minusMonths(1), StatusMensalidade.PAGA);
        assertThat(job.marcarAtrasadas()).isZero();
    }

    @Test
    void baixaGravaAuditoriaENotifica() throws Exception {
        Contrato c = ativo();
        Mensalidade m = fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PENDENTE);

        fazPost("/mensalidades/" + m.getId() + "/baixa", Map.of("dataPagamento", "2026-11-02"), mot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.dataPagamento").value("2026-11-02"))
                .andExpect(jsonPath("$.aluno.id").value(aluno.getId()))
                .andExpect(jsonPath("$.responsavel.id").value(resp.getId()))
                .andExpect(jsonPath("$.motorista.id").value(mot.getId()));

        assertThat(auditorias("Mensalidade", m.getId())).hasSize(1);
        assertThat(auditorias("Mensalidade", m.getId()).getFirst().getAcao()).isEqualTo("BAIXA_MENSALIDADE");
        assertThat(notificacoesDe(resp, TipoNotificacao.MENSALIDADE_PAGA)).hasSize(1);
    }

    @Test
    void baixaSemCorpoUsaHojeEAceitaAtrasada() throws Exception {
        Contrato c = ativo();
        Mensalidade m = fabrica.mensalidade(c, relogio.hoje(), relogio.hoje().minusDays(3),
                StatusMensalidade.ATRASADA, null);

        fazPost("/mensalidades/" + m.getId() + "/baixa", mot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.dataPagamento").value("2026-11-03"));
    }

    @Test
    void baixaDeMensalidadeJaPagaDa409() throws Exception {
        Contrato c = ativo();
        Mensalidade m = fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PAGA);

        fazPost("/mensalidades/" + m.getId() + "/baixa", mot)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MENSALIDADE_JA_PAGA"));
    }

    @Test
    void baixaDeOutroMotoristaDa403() throws Exception {
        Contrato c = ativo();
        Mensalidade m = fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PENDENTE);

        fazPost("/mensalidades/" + m.getId() + "/baixa", fabrica.motorista())
                .andExpect(status().isForbidden());
        assertThat(mensalidades.findById(m.getId()).orElseThrow().getStatus()).isEqualTo(StatusMensalidade.PENDENTE);
    }

    @Test
    void baixaPorResponsavelDa403() throws Exception {
        Contrato c = ativo();
        Mensalidade m = fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PENDENTE);

        fazPost("/mensalidades/" + m.getId() + "/baixa", resp).andExpect(status().isForbidden());
    }

    @Test
    void baixaComDataFuturaDa422() throws Exception {
        Contrato c = ativo();
        Mensalidade m = fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PENDENTE);

        fazPost("/mensalidades/" + m.getId() + "/baixa", Map.of("dataPagamento", "2026-11-04"), mot)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("DATA_FUTURA"));
    }

    @Test
    void baixaInexistenteDa404() throws Exception {
        fazPost("/mensalidades/99999/baixa", mot).andExpect(status().isNotFound());
    }

    @Test
    void listagemPorContratoSoParaAsPartesEmCompetenciaDecrescente() throws Exception {
        Contrato c = ativo();
        fabrica.mensalidade(c, LocalDate.of(2026, 9, 1), StatusMensalidade.PAGA);
        fabrica.mensalidade(c, LocalDate.of(2026, 11, 1), StatusMensalidade.PENDENTE);
        fabrica.mensalidade(c, LocalDate.of(2026, 10, 1), StatusMensalidade.PAGA);

        for (Usuario parte : List.of(resp, mot)) {
            fazGet("/contratos/" + c.getId() + "/mensalidades", parte)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].competencia").value("2026-11-01"))
                    .andExpect(jsonPath("$[2].competencia").value("2026-09-01"));
        }
        fazGet("/contratos/" + c.getId() + "/mensalidades", fabrica.responsavel())
                .andExpect(status().isForbidden());
        fazGet("/contratos/99999/mensalidades", resp).andExpect(status().isNotFound());
    }

    @Test
    void listagemDoResponsavelComFiltroDeStatus() throws Exception {
        Contrato c = ativo();
        fabrica.mensalidade(c, LocalDate.of(2026, 10, 1), StatusMensalidade.PAGA);
        fabrica.mensalidade(c, LocalDate.of(2026, 11, 1), StatusMensalidade.PENDENTE);
        Responsavel outro = fabrica.responsavel();
        Aluno alunoOutro = fabrica.aluno(outro, escola);
        fabrica.mensalidade(fabrica.contrato(outro, mot, alunoOutro, StatusContrato.ATIVO),
                relogio.hoje(), StatusMensalidade.PENDENTE);

        fazGet("/responsavel/mensalidades", resp)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        fazGet("/responsavel/mensalidades?status=PAGA", resp)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PAGA"));
        fazGet("/responsavel/mensalidades", mot).andExpect(status().isForbidden());
    }

    @Test
    void cobrancasDoMotoristaComFiltros() throws Exception {
        Contrato c = ativo();
        fabrica.mensalidade(c, LocalDate.of(2026, 10, 1), StatusMensalidade.PAGA);
        fabrica.mensalidade(c, LocalDate.of(2026, 11, 1), StatusMensalidade.PENDENTE);
        Motorista outroMot = fabrica.motorista();
        Aluno a2 = fabrica.aluno(resp, escola);
        fabrica.mensalidade(fabrica.contrato(resp, outroMot, a2, StatusContrato.ATIVO),
                relogio.hoje(), StatusMensalidade.PENDENTE);

        fazGet("/motorista/cobrancas", mot).andExpect(jsonPath("$.length()").value(2));
        fazGet("/motorista/cobrancas?status=PENDENTE", mot).andExpect(jsonPath("$.length()").value(1));
        fazGet("/motorista/cobrancas?competencia=2026-10", mot)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PAGA"))
                .andExpect(jsonPath("$[0].aluno.nome").value(aluno.getNome()));
        fazGet("/motorista/cobrancas?competencia=2026-10&status=PENDENTE", mot)
                .andExpect(jsonPath("$.length()").value(0));
        fazGet("/motorista/cobrancas?competencia=abc", mot).andExpect(status().isBadRequest());
        fazGet("/motorista/cobrancas", resp).andExpect(status().isForbidden());
    }

    @Test
    void existePendenteOuAtrasada() {
        Contrato c = ativo();
        assertThat(consulta.existePendenteOuAtrasada(c.getId())).isFalse();

        fabrica.mensalidade(c, LocalDate.of(2026, 9, 1), StatusMensalidade.PAGA);
        assertThat(consulta.existePendenteOuAtrasada(c.getId())).isFalse();

        fabrica.mensalidade(c, LocalDate.of(2026, 10, 1), StatusMensalidade.ATRASADA);
        assertThat(consulta.existePendenteOuAtrasada(c.getId())).isTrue();

        Contrato c2 = fabrica.contrato(resp, mot, fabrica.aluno(resp, escola), StatusContrato.ATIVO);
        fabrica.mensalidade(c2, LocalDate.of(2026, 11, 1), StatusMensalidade.PENDENTE);
        assertThat(consulta.existePendenteOuAtrasada(c2.getId())).isTrue();
    }
}
