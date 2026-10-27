package br.com.smartvan.auth;

import br.com.smartvan.enums.StatusMotorista;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.suporte.Fabrica;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import br.com.smartvan.repositorio.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegracaoTest extends TesteIntegracaoBase {

    @Autowired UsuarioRepository usuarioRepository;

    private Map<String, Object> cadastro(String tipo, String email, String cpf) {
        return Map.of("nome", "Fulano de Tal", "email", email, "senha", "segredo1", "cpf", cpf,
                "telefone", "11999998888", "tipo", tipo);
    }

    @Test
    void cadastroDeResponsavelDevolve201SemSenhaEComContaAtiva() throws Exception {
        fazPost("/auth/cadastro", cadastro("RESPONSAVEL", "novo@teste.com", "12345678901"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("novo@teste.com"))
                .andExpect(jsonPath("$.tipo").value("RESPONSAVEL"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.statusValidacao").doesNotExist());
        var salvo = usuarioRepository.findAll().getFirst();
        assertThat(salvo.getSenha()).startsWith("$2"); // BCrypt
    }

    @Test
    void cadastroDeMotoristaNasceComValidacaoPendente() throws Exception {
        fazPost("/auth/cadastro", cadastro("MOTORISTA", "mot@teste.com", "12345678902"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("MOTORISTA"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.statusValidacao").value("PENDENTE"));
    }

    @Test
    void cadastroDeAdministradorDevolve400Validacao() throws Exception {
        fazPost("/auth/cadastro", cadastro("ADMINISTRADOR", "adm@teste.com", "12345678903"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.tipo").exists());
        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void cadastroComCamposInvalidosDevolve400ComCampos() throws Exception {
        fazPost("/auth/cadastro", Map.of("nome", "", "email", "x", "senha", "123", "cpf", "12",
                "telefone", "1", "tipo", "RESPONSAVEL"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.senha").exists())
                .andExpect(jsonPath("$.campos.cpf").exists());
    }

    @Test
    void emailDuplicadoDevolve409() throws Exception {
        fazPost("/auth/cadastro", cadastro("RESPONSAVEL", "dup@teste.com", "12345678904"), null).andExpect(status().isCreated());
        fazPost("/auth/cadastro", cadastro("RESPONSAVEL", "DUP@teste.com", "12345678905"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    void cpfDuplicadoDevolve409() throws Exception {
        fazPost("/auth/cadastro", cadastro("RESPONSAVEL", "a@teste.com", "12345678906"), null).andExpect(status().isCreated());
        fazPost("/auth/cadastro", cadastro("MOTORISTA", "b@teste.com", "12345678906"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CPF_JA_CADASTRADO"));
    }

    @Test
    void loginCorretoDevolveTokenUsuarioEAtualizaUltimoAcesso() throws Exception {
        Responsavel r = fabrica.responsavel();
        String resposta = fazPost("/auth/login", Map.of("email", r.getEmail(), "senha", Fabrica.SENHA), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiraEm").value("2026-11-03T20:00:00Z")) // 09:00 SP + 8 h
                .andExpect(jsonPath("$.usuario.id").value(r.getId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(usuarioRepository.findById(r.getId()).orElseThrow().getUltimoAcesso()).isEqualTo(relogio.instant());

        String token = json.readTree(resposta).get("token").asString();
        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(r.getId()))
                .andExpect(jsonPath("$.tipo").value("RESPONSAVEL"));
    }

    @Test
    void meDeMotoristaTraStatusValidacao() throws Exception {
        var m = fabrica.motorista(StatusMotorista.EM_ANALISE);
        fazGet("/auth/me", m)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusValidacao").value("EM_ANALISE"));
    }

    @Test
    void loginComSenhaErradaOuEmailInexistenteDevolve401() throws Exception {
        Responsavel r = fabrica.responsavel();
        fazPost("/auth/login", Map.of("email", r.getEmail(), "senha", "errada"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
        fazPost("/auth/login", Map.of("email", "nao@existe.com", "senha", "qualquer"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void rotaProtegidaSemTokenDevolve401NoFormatoDoContrato() throws Exception {
        fazGet("/auth/me", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
                .andExpect(jsonPath("$.timestamp").exists());
        fazGet("/motorista/veiculos", null).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoDevolve401() throws Exception {
        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer lixo.lixo.lixo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void tokenExpiradoDevolve401() throws Exception {
        Responsavel r = fabrica.responsavel();
        String token = tokens.para(r);
        relogio.avancarDias(1); // passa das 8 h de validade
        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void papelErradoDevolve403NoFormatoDoContrato() throws Exception {
        Responsavel r = fabrica.responsavel();
        var m = fabrica.motorista();
        fazGet("/motorista/veiculos", r)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        fazGet("/responsavel/alunos", m)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void escolasEHealthSaoPublicos() throws Exception {
        fazGet("/escolas", null).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void corsLiberaOrigemConfigurada() throws Exception {
        mvc.perform(options("/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8081"));
        mvc.perform(post("/auth/login").header(HttpHeaders.ORIGIN, "http://outro.com")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@a.com\",\"senha\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void segredoJwtCurtoImpedeAInicializacao() {
        assertThatThrownBy(() -> new SegurancaConfig("curto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
        assertThatThrownBy(() -> new SegurancaConfig(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
