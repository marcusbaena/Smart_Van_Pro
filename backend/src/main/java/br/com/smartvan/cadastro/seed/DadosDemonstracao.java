package br.com.smartvan.cadastro.seed;

import br.com.smartvan.enums.*;
import br.com.smartvan.model.*;
import br.com.smartvan.repositorio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Dados de demonstração do perfil dev (docs/CONTRATO_API.md, seção 7). Senha de todos: smartvan123.
 * Idempotente: se já existir algum usuário, não faz nada. Nomes de escolas inventados; endereços reais de SP.
 */
@Slf4j
@Component
@Profile("dev")
public class DadosDemonstracao implements CommandLineRunner {

    public static final String SENHA = "smartvan123";

    private final UsuarioRepository usuarios;
    private final ResponsavelRepository responsaveis;
    private final MotoristaRepository motoristas;
    private final EscolaRepository escolas;
    private final EnderecoRepository enderecos;
    private final AlunoRepository alunos;
    private final ResponsavelAlunoRepository vinculos;
    private final VeiculoRepository veiculos;
    private final ContratoRepository contratos;
    private final MensalidadeRepository mensalidades;
    private final br.com.smartvan.cadastro.repository.AlunoEnderecoBuscaRepository alunosEnderecos;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final int diaVencimento;

    public DadosDemonstracao(UsuarioRepository usuarios, ResponsavelRepository responsaveis,
                             MotoristaRepository motoristas, EscolaRepository escolas, EnderecoRepository enderecos,
                             AlunoRepository alunos, ResponsavelAlunoRepository vinculos, VeiculoRepository veiculos,
                             ContratoRepository contratos, MensalidadeRepository mensalidades,
                             br.com.smartvan.cadastro.repository.AlunoEnderecoBuscaRepository alunosEnderecos,
                             PasswordEncoder encoder, Clock clock,
                             @org.springframework.beans.factory.annotation.Value("${smartvan.mensalidade.dia-vencimento:10}") int diaVencimento) {
        this.usuarios = usuarios;
        this.responsaveis = responsaveis;
        this.motoristas = motoristas;
        this.escolas = escolas;
        this.enderecos = enderecos;
        this.alunos = alunos;
        this.vinculos = vinculos;
        this.veiculos = veiculos;
        this.contratos = contratos;
        this.mensalidades = mensalidades;
        this.alunosEnderecos = alunosEnderecos;
        this.encoder = encoder;
        this.clock = clock;
        this.diaVencimento = diaVencimento;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarios.count() > 0) {
            log.info("Seed de demonstração ignorado: já existem usuários.");
            return;
        }
        String hash = encoder.encode(SENHA);

        Escola girassol = escola("Colégio Girassol", "1130000001", "Rua dos Pinheiros", "1200", "Pinheiros",
                "05422001", "-23.5673000", "-46.6896000");
        Escola aquarela = escola("Escola Aquarela", "1130000002", "Rua Fradique Coutinho", "900", "Vila Madalena",
                "05416011", "-23.5565000", "-46.6914000");
        Escola horizonte = escola("Instituto Horizonte", "1130000003", "Rua Cardoso de Almeida", "800", "Perdizes",
                "05013001", "-23.5384000", "-46.6736000");

        Responsavel ana = responsavel("Ana Souza", "ana@demo.com", "11111111111", "11999990001", hash);
        Responsavel carlos = responsavel("Carlos Lima", "carlos@demo.com", "22222222222", "11999990002", hash);

        aluno(ana, "Lucas Souza", LocalDate.of(2015, 3, 12), "4º ano", girassol, Parentesco.MAE,
                end("Rua Mourato Coelho", "500", "Vila Madalena", "05417001", "-23.5586000", "-46.6948000"));
        aluno(ana, "Beatriz Souza", LocalDate.of(2012, 8, 5), "7º ano", horizonte, Parentesco.MAE,
                end("Rua Apinajés", "1000", "Perdizes", "05017000", "-23.5307000", "-46.6710000"));
        Aluno pedro = aluno(carlos, "Pedro Lima", LocalDate.of(2014, 6, 20), "5º ano", aquarela, Parentesco.PAI,
                end("Rua Wisard", "300", "Vila Madalena", "05434080", "-23.5572000", "-46.6987000"));

        Motorista joao = motorista("João Ferreira", "joao@demo.com", "33333333333", "11999990003", hash,
                StatusMotorista.VALIDADO);
        joao.setEnderecoPartida(end("Rua Henrique Schaumann", "270", "Pinheiros", "05413010",
                "-23.5630000", "-46.6970000"));
        joao.getEscolasAtendidas().add(girassol);
        joao.getEscolasAtendidas().add(aquarela);
        motoristas.save(joao);
        veiculos.save(new Veiculo(joao, TipoVeiculo.VAN, "ABC1D23", "Mercedes-Benz Sprinter", 15, 2021));

        Motorista marta = motorista("Marta Oliveira", "marta@demo.com", "44444444444", "11999990004", hash,
                StatusMotorista.VALIDADO);
        marta.setEnderecoPartida(end("Rua Cardoso de Almeida", "1500", "Perdizes", "05013002",
                "-23.5410000", "-46.6770000"));
        marta.getEscolasAtendidas().add(horizonte);
        motoristas.save(marta);
        veiculos.save(new Veiculo(marta, TipoVeiculo.MICRO_ONIBUS, "XYZ9K88", "Volare W9", 20, 2020));

        motorista("Rafael Costa", "rafael@demo.com", "55555555555", "11999990005", hash, StatusMotorista.PENDENTE);

        LocalDate hoje = LocalDate.now(clock);
        Contrato contrato = new Contrato();
        contrato.setResponsavel(carlos);
        contrato.setMotorista(joao);
        contrato.setAluno(pedro);
        contrato.setValorMensal(new BigDecimal("350.00"));
        contrato.setDataInicio(hoje);
        contrato.setStatus(StatusContrato.ATIVO);
        contratos.save(contrato);

        Mensalidade m = new Mensalidade();
        m.setContrato(contrato);
        m.setCompetencia(hoje.withDayOfMonth(1));
        m.setValor(contrato.getValorMensal());
        LocalDate vencimento = hoje.withDayOfMonth(diaVencimento);
        m.setVencimento(hoje.isAfter(vencimento) ? hoje.plusDays(5) : vencimento);
        m.setStatus(StatusMensalidade.PENDENTE);
        mensalidades.save(m);

        log.info("Seed de demonstração criado (senha de todos: {}).", SENHA);
    }

    private Endereco end(String logradouro, String numero, String bairro, String cep, String lat, String lng) {
        Endereco e = new Endereco();
        e.setLogradouro(logradouro);
        e.setNumero(numero);
        e.setBairro(bairro);
        e.setCidade("São Paulo");
        e.setUf("SP");
        e.setCep(cep);
        e.setLatitude(new BigDecimal(lat));
        e.setLongitude(new BigDecimal(lng));
        return enderecos.save(e);
    }

    private Escola escola(String nome, String telefone, String logradouro, String numero, String bairro, String cep,
                          String lat, String lng) {
        Escola e = new Escola();
        e.setNome(nome);
        e.setTelefone(telefone);
        e.setEndereco(end(logradouro, numero, bairro, cep, lat, lng));
        return escolas.save(e);
    }

    private void preencher(Usuario u, String nome, String email, String cpf, String telefone, String hash,
                           TipoUsuario tipo) {
        u.setNome(nome);
        u.setEmail(email);
        u.setCpf(cpf);
        u.setTelefone(telefone);
        u.setSenha(hash);
        u.setTipo(tipo);
        u.setStatus(StatusConta.ATIVA);
    }

    private Responsavel responsavel(String nome, String email, String cpf, String telefone, String hash) {
        Responsavel r = new Responsavel();
        preencher(r, nome, email, cpf, telefone, hash, TipoUsuario.RESPONSAVEL);
        return responsaveis.save(r);
    }

    private Motorista motorista(String nome, String email, String cpf, String telefone, String hash,
                                StatusMotorista status) {
        Motorista m = new Motorista();
        preencher(m, nome, email, cpf, telefone, hash, TipoUsuario.MOTORISTA);
        m.setStatusValidacao(status);
        return motoristas.save(m);
    }

    private Aluno aluno(Responsavel responsavel, String nome, LocalDate nascimento, String serie, Escola escola,
                        Parentesco parentesco, Endereco endereco) {
        Aluno a = new Aluno();
        a.setNome(nome);
        a.setDataNascimento(nascimento);
        a.setSerie(serie);
        a.setEscola(escola);
        a = alunos.save(a);

        AlunoEndereco ae = new AlunoEndereco();
        ae.setAluno(a);
        ae.setEndereco(endereco);
        ae.setTipo(TipoEndereco.RESIDENCIAL);
        ae.setPrincipal(true);
        alunosEnderecos.save(ae);

        ResponsavelAluno v = new ResponsavelAluno();
        v.setResponsavel(responsavel);
        v.setAluno(a);
        v.setParentesco(parentesco);
        v.setStatusVinculo(StatusVinculo.ATIVO);
        v.setAcompanhaRota(true);
        vinculos.save(v);
        return a;
    }
}
