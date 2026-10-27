package br.com.smartvan.rota;

import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.Escola;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FaltaIntegracaoTest extends TesteIntegracaoBase {

    private Aluno alunoDe(Responsavel r) {
        Escola escola = fabrica.escola();
        return fabrica.aluno(r, escola);
    }

    @Test
    void responsavelRegistraFaltaFutura() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);
        String data = relogio.hoje().plusDays(2).toString();

        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", data, "motivo", "consulta"), r)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.data").value(data))
                .andExpect(jsonPath("$.motivo").value("consulta"));
    }

    @Test
    void faltaHojeEPermitidaEMotivoOpcional() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);

        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", relogio.hoje().toString()), r)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    void faltaEmDataPassadaDa422() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);

        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", relogio.hoje().minusDays(1).toString()), r)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("DATA_PASSADA"));
    }

    @Test
    void faltaDuplicadaDa409() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);
        String data = relogio.hoje().plusDays(1).toString();
        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", data), r).andExpect(status().isCreated());

        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", data), r)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FALTA_JA_REGISTRADA"));
    }

    @Test
    void faltaSemDataDa400() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);

        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("motivo", "x"), r)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void faltaDeAlunoDeOutroResponsavelDa403() throws Exception {
        Responsavel dono = fabrica.responsavel();
        Responsavel outro = fabrica.responsavel();
        Aluno aluno = alunoDe(dono);

        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", relogio.hoje().plusDays(1).toString()), outro)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        fazGet("/alunos/" + aluno.getId() + "/faltas", outro).andExpect(status().isForbidden());
        fazDelete("/alunos/" + aluno.getId() + "/faltas/" + relogio.hoje().plusDays(1), outro)
                .andExpect(status().isForbidden());
    }

    @Test
    void alunoInexistenteDa404() throws Exception {
        Responsavel r = fabrica.responsavel();

        fazPost("/alunos/999999/faltas", Map.of("data", relogio.hoje().plusDays(1).toString()), r)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void motoristaNaoRegistraFalta() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);

        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", relogio.hoje().plusDays(1).toString()),
                fabrica.motorista()).andExpect(status().isForbidden());
    }

    @Test
    void listaFaltasDoMes() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);
        // hoje = 2026-11-03
        fabrica.falta(aluno, java.time.LocalDate.of(2026, 11, 20));
        fabrica.falta(aluno, java.time.LocalDate.of(2026, 11, 5));
        fabrica.falta(aluno, java.time.LocalDate.of(2026, 12, 1));

        fazGet("/alunos/" + aluno.getId() + "/faltas?mes=2026-11", r)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].data").value("2026-11-05"))
                .andExpect(jsonPath("$[1].data").value("2026-11-20"));
        fazGet("/alunos/" + aluno.getId() + "/faltas?mes=2026-12", r)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void removeFalta() throws Exception {
        Responsavel r = fabrica.responsavel();
        Aluno aluno = alunoDe(r);
        String data = relogio.hoje().plusDays(3).toString();
        fazPost("/alunos/" + aluno.getId() + "/faltas", Map.of("data", data), r).andExpect(status().isCreated());

        fazDelete("/alunos/" + aluno.getId() + "/faltas/" + data, r).andExpect(status().isNoContent());
        fazGet("/alunos/" + aluno.getId() + "/faltas?mes=" + data.substring(0, 7), r)
                .andExpect(jsonPath("$.length()").value(0));
        fazDelete("/alunos/" + aluno.getId() + "/faltas/" + data, r)
                .andExpect(status().isNotFound());
    }
}
