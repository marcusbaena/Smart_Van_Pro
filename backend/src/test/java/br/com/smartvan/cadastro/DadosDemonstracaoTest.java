package br.com.smartvan.cadastro;

import br.com.smartvan.cadastro.seed.DadosDemonstracao;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sobe o contexto com o perfil dev (H2) e confere a tabela da seção 7 do contrato. */
@ActiveProfiles({"test", "dev"})
class DadosDemonstracaoTest extends TesteIntegracaoBase {

    @Autowired DadosDemonstracao seed;
    @Autowired JdbcTemplate jdbc;

    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void criaOsDadosDoContratoEEhIdempotente() throws Exception {
        seed.run();
        seed.run(); // segunda execução não duplica

        assertThat(contar("usuarios")).isEqualTo(5);
        assertThat(contar("escolas")).isEqualTo(3);
        assertThat(contar("alunos")).isEqualTo(3);
        assertThat(contar("veiculos")).isEqualTo(2);
        assertThat(contar("contratos")).isEqualTo(1);
        assertThat(contar("mensalidades")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from enderecos where latitude is null", Integer.class)).isZero();

        Map<String, Object> contrato = jdbc.queryForMap("select status, valor_mensal from contratos");
        assertThat(contrato.get("status")).isEqualTo("ATIVO");
        assertThat(jdbc.queryForObject("select count(*) from motoristas where status_validacao = 'VALIDADO'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from motoristas where status_validacao = 'PENDENTE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from responsaveis_alunos ra join usuarios u on u.usuario_id = ra.responsavel_id where u.email = 'ana@demo.com'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(distinct a.escola_id) from alunos a join responsaveis_alunos ra on ra.aluno_id = a.aluno_id "
                        + "join usuarios u on u.usuario_id = ra.responsavel_id where u.email = 'ana@demo.com'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void loginDeTodosOsUsuariosDemoFunciona() throws Exception {
        seed.run();
        for (String email : new String[]{"ana@demo.com", "carlos@demo.com", "joao@demo.com", "marta@demo.com", "rafael@demo.com"}) {
            fazPost("/auth/login", Map.of("email", email, "senha", "smartvan123"), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usuario.email").value(email));
        }
    }
}
