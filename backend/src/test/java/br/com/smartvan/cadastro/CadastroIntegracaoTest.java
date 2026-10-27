package br.com.smartvan.cadastro;

import br.com.smartvan.enums.StatusVinculo;
import br.com.smartvan.enums.TipoEndereco;
import br.com.smartvan.model.Escola;
import br.com.smartvan.model.Motorista;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.repositorio.MotoristaRepository;
import br.com.smartvan.suporte.FakeMapsProvider;
import br.com.smartvan.suporte.TesteIntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CadastroIntegracaoTest extends TesteIntegracaoBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired MotoristaRepository motoristaRepository;

    private Map<String, Object> endereco(boolean comCoordenadas) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("logradouro", "Rua Mourato Coelho");
        e.put("numero", "500");
        e.put("bairro", "Vila Madalena");
        e.put("cidade", "São Paulo");
        e.put("uf", "SP");
        e.put("cep", "05417001");
        if (comCoordenadas) {
            e.put("latitude", -23.5586);
            e.put("longitude", -46.6948);
        }
        return e;
    }

    private Map<String, Object> aluno(Long escolaId, boolean comCoordenadas) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("nome", "Lucas");
        a.put("dataNascimento", "2015-03-12");
        a.put("serie", "4º ano");
        a.put("escolaId", escolaId);
        a.put("parentesco", "MAE");
        a.put("endereco", endereco(comCoordenadas));
        return a;
    }

    private Map<String, Object> veiculo(String placa, int capacidade) {
        return Map.of("placa", placa, "modelo", "Sprinter", "ano", 2021, "capacidade", capacidade, "tipo", "VAN");
    }

    // ---------------------------------------------------------------- escolas

    @Test
    void listaEscolasComFiltrosDeCidadeENome() throws Exception {
        fabrica.escola("Colégio Girassol", "São Paulo", "-23.56", "-46.69");
        fabrica.escola("Escola Aquarela", "São Paulo", "-23.55", "-46.69");
        fabrica.escola("Colégio Sol", "Campinas", "-22.90", "-47.06");

        fazGet("/escolas", null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].endereco.latitude").exists());
        fazGet("/escolas?cidade=campinas", null).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Colégio Sol"));
        fazGet("/escolas?nome=aquarela&cidade=são", null).andExpect(jsonPath("$.length()").value(1));
        fazGet("/escolas?nome=nada", null).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------- alunos

    @Test
    void cadastroDeAlunoGravaAsTresTabelas() throws Exception {
        Responsavel r = fabrica.responsavel();
        Escola escola = fabrica.escola();

        fazPost("/responsavel/alunos", aluno(escola.getId(), true), r)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Lucas"))
                .andExpect(jsonPath("$.parentesco").value("MAE"))
                .andExpect(jsonPath("$.escola.id").value(escola.getId()))
                .andExpect(jsonPath("$.escola.nome").value(escola.getNome()))
                .andExpect(jsonPath("$.endereco.id").isNumber())
                .andExpect(jsonPath("$.endereco.latitude").value(-23.5586));

        assertThat(jdbc.queryForObject("select count(*) from alunos", Integer.class)).isEqualTo(1);
        Map<String, Object> ae = jdbc.queryForMap("select tipo, principal from alunos_enderecos");
        assertThat(ae.get("tipo")).isEqualTo(TipoEndereco.RESIDENCIAL.name());
        assertThat(ae.get("principal")).isEqualTo(true);
        Map<String, Object> ra = jdbc.queryForMap(
                "select responsavel_id, status_vinculo, acompanha_rota, parentesco from responsaveis_alunos");
        assertThat(((Number) ra.get("responsavel_id")).longValue()).isEqualTo(r.getId());
        assertThat(ra.get("status_vinculo")).isEqualTo(StatusVinculo.ATIVO.name());
        assertThat(ra.get("acompanha_rota")).isEqualTo(true);
        assertThat(ra.get("parentesco")).isEqualTo("MAE");
        assertThat(maps.geocodificacoes).isEmpty(); // veio com coordenadas
    }

    @Test
    void enderecoSemCoordenadaChamaGeocodificacaoESalvaOResultado() throws Exception {
        Responsavel r = fabrica.responsavel();
        Escola escola = fabrica.escola();

        fazPost("/responsavel/alunos", aluno(escola.getId(), false), r)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco.latitude").value(FakeMapsProvider.COORDENADA_PADRAO.latitude().doubleValue()))
                .andExpect(jsonPath("$.endereco.longitude").value(FakeMapsProvider.COORDENADA_PADRAO.longitude().doubleValue()));

        assertThat(maps.geocodificacoes).hasSize(1);
        assertThat(maps.geocodificacoes.getFirst()).contains("Rua Mourato Coelho", "500", "Vila Madalena", "São Paulo", "SP", "05417001");
    }

    @Test
    void semGeocodificacaoSalvaEnderecoSemCoordenada() throws Exception {
        maps.naoGeocodificar();
        Responsavel r = fabrica.responsavel();
        Escola escola = fabrica.escola();

        fazPost("/responsavel/alunos", aluno(escola.getId(), false), r)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco.id").isNumber())
                .andExpect(jsonPath("$.endereco.latitude").doesNotExist());
        assertThat(maps.geocodificacoes).hasSize(1);
    }

    @Test
    void alunoComEscolaInexistenteDevolve404() throws Exception {
        fazPost("/responsavel/alunos", aluno(999999L, true), fabrica.responsavel())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void alunoInvalidoDevolve400() throws Exception {
        Map<String, Object> a = aluno(fabrica.escola().getId(), true);
        a.put("nome", "");
        a.put("parentesco", null);
        ((Map<String, Object>) a.get("endereco")).put("cep", "123");
        fazPost("/responsavel/alunos", a, fabrica.responsavel())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.parentesco").exists())
                .andExpect(jsonPath("$.campos['endereco.cep']").exists());
    }

    @Test
    void listaSoOsAlunosDoResponsavelComEscolaEEndereco() throws Exception {
        Responsavel ana = fabrica.responsavel();
        Responsavel outro = fabrica.responsavel();
        Escola escola = fabrica.escola();
        fabrica.aluno(ana, escola);
        fabrica.aluno(ana, escola);
        fabrica.aluno(outro, escola);

        fazGet("/responsavel/alunos", ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].escola.nome").value(escola.getNome()))
                .andExpect(jsonPath("$[0].endereco.latitude").exists())
                .andExpect(jsonPath("$[0].parentesco").value("MAE"));
        fazGet("/responsavel/alunos", fabrica.responsavel()).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------- veículos

    @Test
    void motoristaCadastraEListaVeiculos() throws Exception {
        Motorista m = fabrica.motorista();
        fazPost("/motorista/veiculos", veiculo("abc1d23", 15), m)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.capacidade").value(15))
                .andExpect(jsonPath("$.tipo").value("VAN"));
        fabrica.veiculo(fabrica.motorista(), 10); // de outro motorista

        fazGet("/motorista/veiculos", m).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placa").value("ABC1D23"));
    }

    @Test
    void placaDuplicadaDevolve409() throws Exception {
        fazPost("/motorista/veiculos", veiculo("ABC1D23", 15), fabrica.motorista()).andExpect(status().isCreated());
        fazPost("/motorista/veiculos", veiculo("abc1d23", 10), fabrica.motorista())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLACA_JA_CADASTRADA"));
    }

    @Test
    void capacidadeForaDoLimiteDevolve400() throws Exception {
        Motorista m = fabrica.motorista();
        fazPost("/motorista/veiculos", veiculo("AAA1A11", 0), m).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.capacidade").exists());
        fazPost("/motorista/veiculos", veiculo("AAA1A12", 61), m).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- escolas atendidas

    @Test
    void motoristaDefineEscolasAtendidasESubstituiAsAnteriores() throws Exception {
        Motorista m = fabrica.motorista();
        Escola a = fabrica.escola("Escola A");
        Escola b = fabrica.escola("Escola B");
        Escola c = fabrica.escola("Escola C");

        fazPut("/motorista/escolas", Map.of("escolaIds", List.of(a.getId(), b.getId())), m)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Escola A"));
        fazPut("/motorista/escolas", Map.of("escolaIds", List.of(c.getId())), m)
                .andExpect(jsonPath("$.length()").value(1));
        fazGet("/motorista/escolas", m)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(c.getId()))
                .andExpect(jsonPath("$[0].endereco.cidade").value("São Paulo"));
        fazPut("/motorista/escolas", Map.of("escolaIds", List.of()), m)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void escolaInexistenteNaListaDeAtendidasDevolve404() throws Exception {
        fazPut("/motorista/escolas", Map.of("escolaIds", List.of(999999)), fabrica.motorista())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    // ---------------------------------------------------------------- ponto de partida e perfil

    @Test
    void enderecoDePartidaGeocodificaEAtualizaNoMesmoRegistro() throws Exception {
        Motorista m = fabrica.motorista();
        long id1 = json.readTree(fazPut("/motorista/endereco-partida", endereco(false), m)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(FakeMapsProvider.COORDENADA_PADRAO.latitude().doubleValue()))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(maps.geocodificacoes).hasSize(1);

        Map<String, Object> novo = endereco(true);
        novo.put("numero", "999");
        fazPut("/motorista/endereco-partida", novo, m)
                .andExpect(jsonPath("$.id").value(id1))
                .andExpect(jsonPath("$.numero").value("999"))
                .andExpect(jsonPath("$.latitude").value(-23.5586));
        assertThat(maps.geocodificacoes).hasSize(1); // com coordenadas não geocodifica de novo
    }

    @Test
    void perfilDoMotoristaReuneUsuarioPartidaVeiculosEEscolas() throws Exception {
        Motorista m = fabrica.motorista();
        Escola escola = fabrica.escola("Escola Perfil");
        fabrica.veiculo(m, 12);
        fabrica.atenderEscolas(m, escola);
        fabrica.enderecoPartida(m);

        fazGet("/motorista/perfil", m)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(m.getId()))
                .andExpect(jsonPath("$.usuario.statusValidacao").value("VALIDADO"))
                .andExpect(jsonPath("$.enderecoPartida.latitude").exists())
                .andExpect(jsonPath("$.veiculos.length()").value(1))
                .andExpect(jsonPath("$.escolas[0].nome").value("Escola Perfil"));
    }

    @Test
    void perfilSemPartidaOmiteOCampo() throws Exception {
        fazGet("/motorista/perfil", fabrica.motorista())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enderecoPartida").doesNotExist())
                .andExpect(jsonPath("$.veiculos.length()").value(0))
                .andExpect(jsonPath("$.escolas.length()").value(0));
    }
}
