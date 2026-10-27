package br.com.smartvan.contratacao;

import br.com.smartvan.comum.evento.ContratoAtivadoEvent;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.StatusMotorista;
import br.com.smartvan.enums.TipoUsuario;
import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.model.Escola;
import br.com.smartvan.model.Motorista;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.notificacao.TipoNotificacao;
import br.com.smartvan.repositorio.ContratoRepository;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class ContratacaoIntegracaoTest extends TesteIntegracaoBase {

    @Autowired ContratoRepository contratoRepository;
    @Autowired ApplicationEvents eventos;

    private Map<String, Object> proposta(Motorista m, Aluno a) {
        return Map.of("motoristaId", m.getId(), "alunoId", a.getId(), "valorMensal", 350);
    }

    private Long propor(Responsavel r, Motorista m, Aluno a) throws Exception {
        String corpo = fazPost("/contratos", proposta(m, a), r).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    // ----------------------------------------------------------------- busca

    @Test
    void buscaSemAlunoDevolve422() throws Exception {
        Responsavel r = fabrica.responsavel();
        fazGet("/motoristas", r).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ALUNO_OBRIGATORIO"));
    }

    @Test
    void buscaExigePapelResponsavel() throws Exception {
        fazGet("/motoristas", fabrica.motorista()).andExpect(status().isForbidden());
        fazGet("/motoristas", null).andExpect(status().isUnauthorized());
    }

    @Test
    void motoristaPendenteNaoApareceNaBuscaNemNoPerfil() throws Exception {
        Responsavel r = fabrica.responsavel();
        fabrica.aluno(r, fabrica.escola());
        Motorista validado = fabrica.motorista("Valido", StatusMotorista.VALIDADO);
        Motorista pendente = fabrica.motorista("Pendente", StatusMotorista.PENDENTE);
        fazGet("/motoristas", r).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(validado.getId()));
        fazGet("/motoristas/" + pendente.getId(), r).andExpect(status().isNotFound());
    }

    @Test
    void filtrosDeEscolaCidadeENomeCombinados() throws Exception {
        Responsavel r = fabrica.responsavel();
        fabrica.aluno(r, fabrica.escola());
        Escola sp = fabrica.escola("Escola SP", "Sao Paulo", "-23.56", "-46.69");
        Escola rio = fabrica.escola("Escola Rio", "Rio de Janeiro", "-22.90", "-43.17");
        Motorista joao = fabrica.motorista("Joao Silva", StatusMotorista.VALIDADO);
        Motorista maria = fabrica.motorista("Maria Souza", StatusMotorista.VALIDADO);
        Motorista joana = fabrica.motorista("Joana Rio", StatusMotorista.VALIDADO);
        fabrica.atenderEscolas(joao, sp);
        fabrica.atenderEscolas(maria, sp, rio);
        fabrica.atenderEscolas(joana, rio);

        fazGet("/motoristas", r).andExpect(jsonPath("$.length()").value(3));
        fazGet("/motoristas?escolaId=" + sp.getId(), r).andExpect(jsonPath("$.length()").value(2));
        fazGet("/motoristas?cidade=rio", r).andExpect(jsonPath("$.length()").value(2));
        fazGet("/motoristas?nome=JOA", r).andExpect(jsonPath("$.length()").value(2));
        fazGet("/motoristas?escolaId=" + sp.getId() + "&cidade=rio", r)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(maria.getId()));
        fazGet("/motoristas?escolaId=" + sp.getId() + "&cidade=rio&nome=joa", r)
                .andExpect(jsonPath("$.length()").value(0));
        fazGet("/motoristas?escolaId=" + sp.getId() + "&nome=joao", r)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(joao.getId()));
    }

    @Test
    void vagasRestantesUsaMaiorCapacidadeEContaSoAtivos() throws Exception {
        Responsavel r = fabrica.responsavel();
        Escola escola = fabrica.escola();
        Aluno a1 = fabrica.aluno(r, escola);
        Aluno a2 = fabrica.aluno(r, escola);
        Aluno a3 = fabrica.aluno(r, escola);
        Motorista m = fabrica.motorista();
        fabrica.atenderEscolas(m, escola);
        fabrica.veiculo(m, 10);
        fabrica.veiculo(m, 15);
        fabrica.contrato(r, m, a1, StatusContrato.ATIVO);
        fabrica.contrato(r, m, a2, StatusContrato.PENDENTE);
        fabrica.contrato(r, m, a3, StatusContrato.RECUSADO);

        fazGet("/motoristas/" + m.getId(), r).andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasRestantes").value(14))
                .andExpect(jsonPath("$.veiculo.capacidade").value(15))
                .andExpect(jsonPath("$.veiculo.tipo").value("VAN"))
                .andExpect(jsonPath("$.escolas[0].id").value(escola.getId()))
                .andExpect(jsonPath("$.cidades.length()").value(1));
    }

    @Test
    void motoristaSemVeiculoTemZeroVagasESemVeiculo() throws Exception {
        Responsavel r = fabrica.responsavel();
        fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        fazGet("/motoristas/" + m.getId(), r).andExpect(jsonPath("$.vagasRestantes").value(0))
                .andExpect(jsonPath("$.veiculo").doesNotExist());
    }

    // -------------------------------------------------------------- proposta

    @Test
    void propostaCriadaPendenteNotificaMotorista() throws Exception {
        Responsavel r = fabrica.responsavel();
        Motorista m = fabrica.motorista();
        Escola escola = fabrica.escola();
        fabrica.veiculo(m, 15);
        Aluno aluno = fabrica.aluno(r, escola);

        fazPost("/contratos", proposta(m, aluno), r).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valorMensal").value(350.0))
                .andExpect(jsonPath("$.dataInicio").value("2026-11-03"))
                .andExpect(jsonPath("$.aluno.id").value(aluno.getId()))
                .andExpect(jsonPath("$.responsavel.id").value(r.getId()))
                .andExpect(jsonPath("$.motorista.id").value(m.getId()))
                .andExpect(jsonPath("$.escola.id").value(escola.getId()))
                .andExpect(jsonPath("$.cancelamento").doesNotExist());
        assertThat(notificacoesDe(m, TipoNotificacao.PROPOSTA_RECEBIDA)).hasSize(1);
    }

    @Test
    void propostaDeMotoristaDevolve403() throws Exception {
        Motorista m = fabrica.motorista();
        Aluno aluno = fabrica.aluno(fabrica.responsavel(), fabrica.escola());
        fazPost("/contratos", proposta(m, aluno), m).andExpect(status().isForbidden());
    }

    @Test
    void propostaValidaCorpo() throws Exception {
        Responsavel r = fabrica.responsavel();
        fazPost("/contratos", Map.of("motoristaId", 1), r).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        Motorista m = fabrica.motorista();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        fazPost("/contratos", Map.of("motoristaId", m.getId(), "alunoId", aluno.getId(), "valorMensal", -5), r)
                .andExpect(status().isBadRequest());
    }

    @Test
    void propostaParaAlunoDeOutroDevolve403() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno alheio = fabrica.aluno(fabrica.responsavel(), fabrica.escola());
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 15);
        fazPost("/contratos", proposta(m, alheio), r).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void propostaComAlunoOuMotoristaInexistenteDevolve404() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        fazPost("/contratos", Map.of("motoristaId", 99999, "alunoId", aluno.getId(), "valorMensal", 300), r)
                .andExpect(status().isNotFound());
        Motorista m = fabrica.motorista();
        fazPost("/contratos", Map.of("motoristaId", m.getId(), "alunoId", 99999, "valorMensal", 300), r)
                .andExpect(status().isNotFound());
    }

    @Test
    void propostaParaMotoristaNaoValidadoDevolve422() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista(StatusMotorista.PENDENTE);
        fabrica.veiculo(m, 15);
        fazPost("/contratos", proposta(m, aluno), r).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTORISTA_NAO_VALIDADO"));
    }

    @Test
    void propostaSemVagaDevolve409() throws Exception {
        Responsavel r = fabrica.responsavel();
        Escola escola = fabrica.escola();
        Aluno a1 = fabrica.aluno(r, escola);
        Aluno a2 = fabrica.aluno(r, escola);
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 1);
        fabrica.contrato(r, m, a1, StatusContrato.ATIVO);
        fazPost("/contratos", proposta(m, a2), r).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEM_VAGAS"));
    }

    @Test
    void propostaParaMotoristaSemVeiculoDevolveSemVagas() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        fazPost("/contratos", proposta(m, aluno), r).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEM_VAGAS"));
    }

    @Test
    void pendentesNaoOcupamVagaNaProposta() throws Exception {
        Responsavel r = fabrica.responsavel();
        Escola escola = fabrica.escola();
        Aluno a1 = fabrica.aluno(r, escola);
        Aluno a2 = fabrica.aluno(r, escola);
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 1);
        fabrica.contrato(r, m, a1, StatusContrato.PENDENTE);
        fazPost("/contratos", proposta(m, a2), r).andExpect(status().isCreated());
    }

    @Test
    void propostaDuplicadaDevolve409() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 15);
        propor(r, m, aluno);
        fazPost("/contratos", proposta(m, aluno), r).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROPOSTA_DUPLICADA"));
    }

    @Test
    void alunoComContratoAtivoNaoPodeReceberNovaProposta() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista atual = fabrica.motorista();
        Motorista outro = fabrica.motorista();
        fabrica.veiculo(atual, 15);
        fabrica.veiculo(outro, 15);
        fabrica.contrato(r, atual, aluno, StatusContrato.ATIVO);
        fazPost("/contratos", proposta(outro, aluno), r).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ALUNO_JA_TEM_CONTRATO"));
    }

    // ----------------------------------------------------------------- aceitar

    @Test
    void aceitarAtivaPublicaEventoENotificaResponsavel() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 15);
        Long id = propor(r, m, aluno);
        relogio.avancarDias(2);

        fazPost("/contratos/" + id + "/aceitar", m).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.dataInicio").value("2026-11-05"));

        assertThat(contratoRepository.findById(id).orElseThrow().getStatus()).isEqualTo(StatusContrato.ATIVO);
        assertThat(eventos.stream(ContratoAtivadoEvent.class).toList())
                .extracting(ContratoAtivadoEvent::contratoId).containsExactly(id);
        assertThat(notificacoesDe(r, TipoNotificacao.PROPOSTA_ACEITA)).hasSize(1);
    }

    @Test
    void aceitarQuandoLotouEntrePropostaEAceiteDevolve409SemVagas() throws Exception {
        Responsavel r1 = fabrica.responsavel();
        Responsavel r2 = fabrica.responsavel();
        Escola escola = fabrica.escola();
        Aluno a1 = fabrica.aluno(r1, escola);
        Aluno a2 = fabrica.aluno(r2, escola);
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 1);
        Long c1 = propor(r1, m, a1);
        Long c2 = propor(r2, m, a2);

        fazPost("/contratos/" + c1 + "/aceitar", m).andExpect(status().isOk());
        fazPost("/contratos/" + c2 + "/aceitar", m).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SEM_VAGAS"));
        assertThat(contratoRepository.findById(c2).orElseThrow().getStatus()).isEqualTo(StatusContrato.PENDENTE);
        assertThat(eventos.stream(ContratoAtivadoEvent.class).count()).isEqualTo(1);
    }

    @Test
    void aceitarContratoNaoPendenteDevolve409() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 15);
        Contrato ativo = fabrica.contrato(r, m, aluno, StatusContrato.ATIVO);
        fazPost("/contratos/" + ativo.getId() + "/aceitar", m).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTRATO_NAO_PENDENTE"));
        assertThat(eventos.stream(ContratoAtivadoEvent.class).count()).isZero();
    }

    @Test
    void motoristaAceitandoContratoDeOutroDevolve403() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista dono = fabrica.motorista();
        Motorista outro = fabrica.motorista();
        fabrica.veiculo(dono, 15);
        Long id = propor(r, dono, aluno);
        fazPost("/contratos/" + id + "/aceitar", outro).andExpect(status().isForbidden());
        fazPost("/contratos/" + id + "/recusar", outro).andExpect(status().isForbidden());
    }

    @Test
    void responsavelNaoPodeAceitarNemRecusar() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 15);
        Long id = propor(r, m, aluno);
        fazPost("/contratos/" + id + "/aceitar", r).andExpect(status().isForbidden());
        fazPost("/contratos/" + id + "/recusar", r).andExpect(status().isForbidden());
    }

    @Test
    void aceitarOuRecusarInexistenteDevolve404() throws Exception {
        Motorista m = fabrica.motorista();
        fazPost("/contratos/99999/aceitar", m).andExpect(status().isNotFound());
        fazPost("/contratos/99999/recusar", m).andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------- recusar

    @Test
    void recusarMudaStatusENotificaResponsavel() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        fabrica.veiculo(m, 15);
        Long id = propor(r, m, aluno);
        fazPost("/contratos/" + id + "/recusar", m).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"));
        assertThat(notificacoesDe(r, TipoNotificacao.PROPOSTA_RECUSADA)).hasSize(1);
        assertThat(eventos.stream(ContratoAtivadoEvent.class).count()).isZero();
    }

    @Test
    void recusarContratoNaoPendenteDevolve409() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        Contrato recusado = fabrica.contrato(r, m, aluno, StatusContrato.RECUSADO);
        fazPost("/contratos/" + recusado.getId() + "/recusar", m).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONTRATO_NAO_PENDENTE"));
    }

    // ---------------------------------------------------------------- consultas

    @Test
    void listaContratosDosDoisPapeisComFiltroDeStatus() throws Exception {
        Responsavel r = fabrica.responsavel();
        Responsavel outroResp = fabrica.responsavel();
        Escola escola = fabrica.escola();
        Aluno a1 = fabrica.aluno(r, escola);
        Aluno a2 = fabrica.aluno(r, escola);
        Aluno a3 = fabrica.aluno(outroResp, escola);
        Motorista m = fabrica.motorista();
        Contrato c1 = fabrica.contrato(r, m, a1, StatusContrato.ATIVO);
        Contrato c2 = fabrica.contrato(r, m, a2, StatusContrato.PENDENTE);
        fabrica.contrato(outroResp, m, a3, StatusContrato.PENDENTE);

        fazGet("/contratos", r).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(c2.getId()))
                .andExpect(jsonPath("$[1].id").value(c1.getId()));
        fazGet("/contratos?status=ATIVO", r).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(c1.getId()));
        fazGet("/contratos", m).andExpect(jsonPath("$.length()").value(3));
        fazGet("/contratos?status=PENDENTE", m).andExpect(jsonPath("$.length()").value(2));
        fazGet("/contratos", fabrica.responsavel()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void obterContratoSoPelasPartes() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        Contrato c = fabrica.contrato(r, m, aluno, StatusContrato.PENDENTE);
        fazGet("/contratos/" + c.getId(), r).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(c.getId()));
        fazGet("/contratos/" + c.getId(), m).andExpect(status().isOk());
        fazGet("/contratos/" + c.getId(), fabrica.responsavel()).andExpect(status().isForbidden());
        fazGet("/contratos/" + c.getId(), fabrica.motorista()).andExpect(status().isForbidden());
        fazGet("/contratos/99999", r).andExpect(status().isNotFound());
    }

    @Test
    void contratoIncluiBlocoCancelamentoQuandoHaSolicitacao() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = fabrica.aluno(r, fabrica.escola());
        Motorista m = fabrica.motorista();
        Contrato c = fabrica.contrato(r, m, aluno, StatusContrato.ATIVO, new BigDecimal("400.00"), relogio.hoje());
        fabrica.comCancelamento(c, TipoUsuario.RESPONSAVEL, relogio.hoje(), "mudanca de cidade");
        fazGet("/contratos/" + c.getId(), r).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelamento.dataSolicitacao").value("2026-11-03"))
                .andExpect(jsonPath("$.cancelamento.dataEncerramentoEfetivo").value("2026-12-03"))
                .andExpect(jsonPath("$.cancelamento.solicitante").value("RESPONSAVEL"))
                .andExpect(jsonPath("$.cancelamento.motivo").value("mudanca de cidade"));
    }
}
