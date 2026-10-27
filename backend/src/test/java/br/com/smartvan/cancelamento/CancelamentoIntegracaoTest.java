package br.com.smartvan.cancelamento;

import br.com.smartvan.cancelamento.service.CancelamentoJob;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.StatusMensalidade;
import br.com.smartvan.enums.TipoUsuario;
import br.com.smartvan.model.*;
import br.com.smartvan.notificacao.TipoNotificacao;
import br.com.smartvan.repositorio.ContratoRepository;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CancelamentoIntegracaoTest extends TesteIntegracaoBase {

    @Autowired CancelamentoJob job;
    @Autowired ContratoRepository contratos;

    Responsavel r;
    Motorista m;
    Aluno aluno;

    @BeforeEach
    void cenario() {
        r = fabrica.responsavel();
        m = fabrica.motorista();
        aluno = fabrica.aluno(r, fabrica.escola());
    }

    Contrato ativo() {
        return fabrica.contrato(r, m, aluno, StatusContrato.ATIVO);
    }

    String url(Contrato c) {
        return "/contratos/" + c.getId() + "/cancelamento";
    }

    Contrato recarrega(Contrato c) {
        return contratos.findById(c.getId()).orElseThrow();
    }

    @Test
    void responsavelSolicita() throws Exception {
        Contrato c = ativo();
        fazPost(url(c), Map.of("motivo", "Mudança de cidade"), r)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(c.getId()))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.cancelamento.dataSolicitacao").value(relogio.hoje().toString()))
                .andExpect(jsonPath("$.cancelamento.dataEncerramentoEfetivo").value(relogio.hoje().plusDays(30).toString()))
                .andExpect(jsonPath("$.cancelamento.solicitante").value("RESPONSAVEL"))
                .andExpect(jsonPath("$.cancelamento.motivo").value("Mudança de cidade"))
                .andExpect(jsonPath("$.aluno.id").value(aluno.getId()))
                .andExpect(jsonPath("$.motorista.id").value(m.getId()));
        assertThat(recarrega(c).getStatus()).isEqualTo(StatusContrato.ATIVO);
        assertThat(notificacoesDe(m, TipoNotificacao.CANCELAMENTO_SOLICITADO)).hasSize(1);
        assertThat(notificacoesDe(r, TipoNotificacao.CANCELAMENTO_SOLICITADO)).isEmpty();
    }

    @Test
    void motoristaSolicita() throws Exception {
        Contrato c = ativo();
        fazPost(url(c), Map.of("motivo", "Sem vaga na van"), m)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelamento.solicitante").value("MOTORISTA"));
        assertThat(notificacoesDe(r, TipoNotificacao.CANCELAMENTO_SOLICITADO)).hasSize(1);
        assertThat(notificacoesDe(m, TipoNotificacao.CANCELAMENTO_SOLICITADO)).isEmpty();
    }

    @Test
    void quemNaoEParteRecebe403() throws Exception {
        Contrato c = ativo();
        fazPost(url(c), Map.of("motivo", "Quero cancelar"), fabrica.responsavel("Outro")).andExpect(status().isForbidden());
        fazPost(url(c), Map.of("motivo", "Quero cancelar"), fabrica.motorista()).andExpect(status().isForbidden());
        assertThat(recarrega(c).getDataSolicitacaoCancelamento()).isNull();
    }

    @Test
    void contratoPendenteOuEncerradoRecebe409() throws Exception {
        for (StatusContrato s : new StatusContrato[]{StatusContrato.PENDENTE, StatusContrato.ENCERRADO}) {
            Contrato c = fabrica.contrato(r, m, aluno, s);
            fazPost(url(c), Map.of("motivo", "Quero cancelar"), r)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONTRATO_NAO_ATIVO"));
        }
    }

    @Test
    void segundaSolicitacaoRecebe409() throws Exception {
        Contrato c = ativo();
        fazPost(url(c), Map.of("motivo", "Quero cancelar"), r).andExpect(status().isOk());
        fazPost(url(c), Map.of("motivo", "Outra vez aqui"), m)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CANCELAMENTO_JA_SOLICITADO"));
    }

    @Test
    void motivoInvalidoRecebe400() throws Exception {
        Contrato c = ativo();
        fazPost(url(c), Map.of("motivo", "abc"), r)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        fazPost(url(c), Map.of("motivo", "x".repeat(501)), r).andExpect(status().isBadRequest());
        fazPost(url(c), Map.of(), r).andExpect(status().isBadRequest());
    }

    @Test
    void contratoInexistenteRecebe404() throws Exception {
        fazPost("/contratos/999999/cancelamento", Map.of("motivo", "Quero cancelar"), r).andExpect(status().isNotFound());
    }

    @Test
    void jobAntesDaDataNaoEncerra() {
        Contrato c = fabrica.comCancelamento(ativo(), TipoUsuario.RESPONSAVEL, relogio.hoje(), "mudança");
        relogio.avancarDias(29);
        job.encerrarVencidos();
        assertThat(recarrega(c).getStatus()).isEqualTo(StatusContrato.ATIVO);
        assertThat(notificacoesDe(r)).isEmpty();
    }

    @Test
    void jobNaDataEncerraSemPendencia() {
        Contrato c = fabrica.comCancelamento(ativo(), TipoUsuario.RESPONSAVEL, relogio.hoje(), "mudança");
        fabrica.mensalidade(c, relogio.hoje(), StatusMensalidade.PAGA);
        relogio.avancarDias(30);
        job.encerrarVencidos();
        assertThat(recarrega(c).getStatus()).isEqualTo(StatusContrato.ENCERRADO);
        assertThat(notificacoesDe(r, TipoNotificacao.CONTRATO_ENCERRADO)).hasSize(1);
        assertThat(notificacoesDe(m, TipoNotificacao.CONTRATO_ENCERRADO)).hasSize(1);
        assertThat(notificacoesDe(r, TipoNotificacao.CANCELAMENTO_COM_PENDENCIA)).isEmpty();
    }

    @Test
    void jobComMensalidadePendenteNotificaPendencia() {
        verificaPendencia(StatusMensalidade.PENDENTE);
    }

    @Test
    void jobComMensalidadeAtrasadaNotificaPendencia() {
        verificaPendencia(StatusMensalidade.ATRASADA);
    }

    private void verificaPendencia(StatusMensalidade s) {
        Contrato c = fabrica.comCancelamento(ativo(), TipoUsuario.MOTORISTA, relogio.hoje(), "mudança");
        fabrica.mensalidade(c, relogio.hoje(), s);
        relogio.avancarDias(31);
        job.encerrarVencidos();
        assertThat(recarrega(c).getStatus()).isEqualTo(StatusContrato.ENCERRADO);
        assertThat(notificacoesDe(r, TipoNotificacao.CANCELAMENTO_COM_PENDENCIA)).hasSize(1);
        assertThat(notificacoesDe(m, TipoNotificacao.CANCELAMENTO_COM_PENDENCIA)).hasSize(1);
        assertThat(notificacoesDe(r, TipoNotificacao.CONTRATO_ENCERRADO)).isEmpty();
    }

    @Test
    void jobNaoTocaContratoSemSolicitacao() {
        Contrato c = ativo();
        relogio.avancarDias(90);
        job.encerrarVencidos();
        assertThat(recarrega(c).getStatus()).isEqualTo(StatusContrato.ATIVO);
        assertThat(notificacoesDe(r)).isEmpty();
    }

    @Test
    void jobNaoRepeteEncerramento() {
        fabrica.comCancelamento(ativo(), TipoUsuario.RESPONSAVEL, relogio.hoje(), "mudança");
        relogio.avancarDias(30);
        job.encerrarVencidos();
        job.encerrarVencidos();
        assertThat(notificacoesDe(r, TipoNotificacao.CONTRATO_ENCERRADO)).hasSize(1);
    }
}
