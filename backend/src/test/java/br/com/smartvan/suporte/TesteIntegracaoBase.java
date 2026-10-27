package br.com.smartvan.suporte;

import br.com.smartvan.auditoria.Auditoria;
import br.com.smartvan.auditoria.AuditoriaRepository;
import br.com.smartvan.model.Usuario;
import br.com.smartvan.notificacao.Notificacao;
import br.com.smartvan.notificacao.NotificacaoRepository;
import br.com.smartvan.notificacao.TipoNotificacao;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base dos testes de integração (H2 em modo PostgreSQL, Flyway real, Clock fixo, MapsProvider falso).
 * Antes de cada teste: limpa todas as tabelas, reinicia o relógio e o FakeMapsProvider.
 * Veja LEIA-ME.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RelogioFixoConfig.class, MapsFalsoConfig.class})
public abstract class TesteIntegracaoBase {

    private static final List<String> TABELAS_EM_ORDEM_DE_LIMPEZA = List.of(
            "paradas_rota", "rotas", "faltas", "mensalidades", "contratos", "notificacoes", "auditorias",
            "documentos", "validacoes", "alunos_enderecos", "responsaveis_alunos", "alunos", "veiculos",
            "motoristas_escolas", "motoristas", "responsaveis", "administradores", "usuarios", "escolas",
            "enderecos");

    @Autowired protected MockMvc mvc;
    @Autowired protected Fabrica fabrica;
    @Autowired protected Tokens tokens;
    @Autowired protected RelogioMutavel relogio;
    @Autowired protected FakeMapsProvider maps;
    @Autowired protected JsonMapper json;
    @Autowired protected NotificacaoRepository notificacaoRepository;
    @Autowired protected AuditoriaRepository auditoriaRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void reiniciarAntesDeCadaTeste() {
        limparBanco();
        relogio.reiniciar();
        maps.reiniciar();
    }

    /** Apaga todas as linhas de todas as tabelas (já roda antes de cada teste; chame de novo se precisar). */
    protected void limparBanco() {
        for (String tabela : TABELAS_EM_ORDEM_DE_LIMPEZA) {
            jdbc.update("delete from " + tabela);
        }
    }

    // ---------------------------------------------------------------- atalhos de MockMvc

    protected ResultActions fazGet(String url, Usuario como) throws Exception {
        return mvc.perform(autenticar(get(url), como));
    }

    protected ResultActions fazPost(String url, Object corpo, Usuario como) throws Exception {
        return mvc.perform(autenticar(post(url), como).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }

    /** POST sem corpo (ex.: aceitar/recusar). */
    protected ResultActions fazPost(String url, Usuario como) throws Exception {
        return mvc.perform(autenticar(post(url), como));
    }

    protected ResultActions fazPut(String url, Object corpo, Usuario como) throws Exception {
        return mvc.perform(autenticar(put(url), como).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }

    protected ResultActions fazDelete(String url, Usuario como) throws Exception {
        return mvc.perform(autenticar(delete(url), como));
    }

    /** Adiciona o header Authorization; {@code como == null} = sem token. */
    protected MockHttpServletRequestBuilder autenticar(MockHttpServletRequestBuilder req, Usuario como) {
        return como == null ? req : req.header(HttpHeaders.AUTHORIZATION, tokens.bearer(como));
    }

    // ---------------------------------------------------------------- verificação de efeitos

    /** Notificações do usuário (mais recentes primeiro). */
    protected List<Notificacao> notificacoesDe(Usuario usuario) {
        return notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuario.getId());
    }

    protected List<Notificacao> notificacoesDe(Usuario usuario, TipoNotificacao tipo) {
        return notificacoesDe(usuario).stream().filter(n -> n.getTipo() == tipo).toList();
    }

    protected List<Auditoria> auditorias(String entidade, Long entidadeId) {
        return auditoriaRepository.findByEntidadeAndEntidadeId(entidade, entidadeId);
    }
}
