package br.com.smartvan.suporte;

import br.com.smartvan.enums.*;
import br.com.smartvan.model.*;
import br.com.smartvan.repositorio.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cria entidades direto pelos repositories (sem passar pela API). Cada método salva e devolve a entidade com id.
 * E-mails, CPFs e placas são gerados únicos. Senha de todos os usuários: {@link #SENHA}.
 * As entidades devolvidas estão destacadas (sem sessão): use os ids e os repositories para reler.
 */
@Component
public class Fabrica {

    public static final String SENHA = "smartvan123";

    private final AtomicLong seq = new AtomicLong(1000);

    private final ResponsavelRepository responsaveis;
    private final MotoristaRepository motoristas;
    private final EscolaRepository escolas;
    private final EnderecoRepository enderecos;
    private final AlunoRepository alunos;
    private final VeiculoRepository veiculos;
    private final ContratoRepository contratos;
    private final MensalidadeRepository mensalidades;
    private final FaltaRepository faltas;
    private final PasswordEncoder encoder;
    private final java.time.Clock relogio;
    private final jakarta.persistence.EntityManager em;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    private String hashSenha;

    public Fabrica(ResponsavelRepository responsaveis, MotoristaRepository motoristas,
                   EscolaRepository escolas, EnderecoRepository enderecos, AlunoRepository alunos,
                   VeiculoRepository veiculos, ContratoRepository contratos,
                   MensalidadeRepository mensalidades, FaltaRepository faltas, PasswordEncoder encoder,
                   java.time.Clock relogio, jakarta.persistence.EntityManager em,
                   org.springframework.transaction.PlatformTransactionManager tm) {
        this.responsaveis = responsaveis;
        this.motoristas = motoristas;
        this.escolas = escolas;
        this.enderecos = enderecos;
        this.alunos = alunos;
        this.veiculos = veiculos;
        this.contratos = contratos;
        this.mensalidades = mensalidades;
        this.faltas = faltas;
        this.encoder = encoder;
        this.relogio = relogio;
        this.em = em;
        this.tx = new org.springframework.transaction.support.TransactionTemplate(tm);
    }

    private long proximo() {
        return seq.incrementAndGet();
    }

    private String hash() {
        if (hashSenha == null) {
            hashSenha = encoder.encode(SENHA);
        }
        return hashSenha;
    }

    // ---------------------------------------------------------------- usuários

    public Responsavel responsavel() {
        return responsavel("Responsável " + proximo());
    }

    public Responsavel responsavel(String nome) {
        long n = proximo();
        Responsavel r = new Responsavel();
        preencher(r, nome, "resp" + n + "@teste.com", n, TipoUsuario.RESPONSAVEL);
        return responsaveis.save(r);
    }

    /** Motorista VALIDADO. */
    public Motorista motorista() {
        return motorista(StatusMotorista.VALIDADO);
    }

    public Motorista motorista(StatusMotorista status) {
        return motorista("Motorista " + proximo(), status);
    }

    public Motorista motorista(String nome, StatusMotorista status) {
        long n = proximo();
        Motorista m = new Motorista();
        preencher(m, nome, "mot" + n + "@teste.com", n, TipoUsuario.MOTORISTA);
        m.setStatusValidacao(status);
        return motoristas.save(m);
    }

    private void preencher(Usuario u, String nome, String email, long n, TipoUsuario tipo) {
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha(hash());
        u.setCpf(String.format("%011d", n));
        u.setTelefone("11" + String.format("%09d", n));
        u.setTipo(tipo);
        u.setStatus(StatusConta.ATIVA);
    }

    // ---------------------------------------------------------------- endereços e escolas

    /** Endereço em Pinheiros (SP) com coordenadas. */
    public Endereco endereco() {
        return endereco("-23.5670000", "-46.7020000");
    }

    public Endereco endereco(String latitude, String longitude) {
        return enderecos.save(montarEndereco(latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude)));
    }

    /** Endereço SEM coordenadas (para testar geocodificação / ENDERECO_SEM_COORDENADAS). */
    public Endereco enderecoSemCoordenadas() {
        return enderecos.save(montarEndereco(null, null));
    }

    private Endereco montarEndereco(BigDecimal lat, BigDecimal lng) {
        long n = proximo();
        Endereco e = new Endereco();
        e.setLogradouro("Rua Teste " + n);
        e.setNumero(String.valueOf(n % 1000));
        e.setBairro("Pinheiros");
        e.setCidade("São Paulo");
        e.setUf("SP");
        e.setCep("05422001");
        e.setLatitude(lat);
        e.setLongitude(lng);
        return e;
    }

    /** Escola em São Paulo com coordenadas (-23.5600, -46.6900). */
    public Escola escola() {
        return escola("Escola " + proximo(), "São Paulo", "-23.5600000", "-46.6900000");
    }

    public Escola escola(String nome) {
        return escola(nome, "São Paulo", "-23.5600000", "-46.6900000");
    }

    public Escola escola(String nome, String cidade, String latitude, String longitude) {
        Endereco e = montarEndereco(latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude));
        e.setCidade(cidade);
        Escola escola = new Escola();
        escola.setNome(nome);
        escola.setTelefone("1130000000");
        escola.setEndereco(enderecos.save(e));
        return escolas.save(escola);
    }

    // ---------------------------------------------------------------- aluno

    /** Aluno do responsável, na escola, com endereço residencial principal em Pinheiros (com coordenadas). */
    public Aluno aluno(Responsavel responsavel, Escola escola) {
        return aluno(responsavel, escola, endereco());
    }

    /** Idem, com o endereço residencial principal à escolha (use {@link #endereco(String, String)}). */
    public Aluno aluno(Responsavel responsavel, Escola escola, Endereco enderecoResidencial) {
        return tx.execute(status -> {
            Aluno a = new Aluno();
            a.setNome("Aluno " + proximo());
            a.setDataNascimento(LocalDate.of(2015, 5, 20));
            a.setSerie("3º ano");
            a.setEscola(em.getReference(Escola.class, escola.getId()));
            em.persist(a);

            AlunoEndereco ae = new AlunoEndereco();
            ae.setAluno(a);
            ae.setEndereco(em.getReference(Endereco.class, enderecoResidencial.getId()));
            ae.setTipo(TipoEndereco.RESIDENCIAL);
            ae.setPrincipal(true);
            em.persist(ae);

            ResponsavelAluno v = new ResponsavelAluno();
            v.setResponsavel(em.getReference(Responsavel.class, responsavel.getId()));
            v.setAluno(a);
            v.setParentesco(Parentesco.MAE);
            v.setStatusVinculo(StatusVinculo.ATIVO);
            v.setAcompanhaRota(true);
            em.persist(v);
            return a;
        });
    }

    public Falta falta(Aluno aluno, LocalDate data) {
        Falta f = new Falta();
        f.setAluno(aluno);
        f.setDataFalta(data);
        f.setMotivo("teste");
        return faltas.save(f);
    }

    // ---------------------------------------------------------------- motorista: veículo, escolas, partida

    public Veiculo veiculo(Motorista motorista, int capacidade) {
        long n = proximo();
        String placa = "TST" + String.format("%04d", n % 10000);
        return veiculos.save(new Veiculo(motorista, TipoVeiculo.VAN, placa, "Van Teste", capacidade, 2022));
    }

    /** Faz o motorista atender as escolas (acrescenta às já atendidas). Devolve o motorista recarregado. */
    @Transactional
    public Motorista atenderEscolas(Motorista motorista, Escola... escolasAtendidas) {
        Motorista m = motoristas.findById(motorista.getId()).orElseThrow();
        m.getEscolasAtendidas().addAll(java.util.List.of(escolasAtendidas));
        return m;
    }

    /** Define o ponto de partida do motorista (endereço com coordenadas). Devolve o endereço. */
    @Transactional
    public Endereco enderecoPartida(Motorista motorista) {
        Endereco e = enderecos.save(montarEndereco(new BigDecimal("-23.5630000"), new BigDecimal("-46.6970000")));
        motoristas.findById(motorista.getId()).orElseThrow().setEnderecoPartida(e);
        return e;
    }

    // ---------------------------------------------------------------- contrato e mensalidade

    /** Contrato de R$ 350,00 com data de início = hoje (relógio do teste), no status pedido. */
    public Contrato contrato(Responsavel responsavel, Motorista motorista, Aluno aluno, StatusContrato status) {
        return contrato(responsavel, motorista, aluno, status, new BigDecimal("350.00"), LocalDate.now(relogio));
    }

    public Contrato contrato(Responsavel responsavel, Motorista motorista, Aluno aluno, StatusContrato status,
                             BigDecimal valorMensal, LocalDate dataInicio) {
        Contrato c = new Contrato();
        c.setResponsavel(responsavel);
        c.setMotorista(motorista);
        c.setAluno(aluno);
        c.setValorMensal(valorMensal);
        c.setDataInicio(dataInicio);
        c.setStatus(status);
        return contratos.save(c);
    }

    /**
     * Marca cancelamento solicitado (o contrato continua no status em que estava). dataEncerramentoEfetivo =
     * dataSolicitacao + 30 dias. Devolve o contrato salvo.
     */
    public Contrato comCancelamento(Contrato contrato, TipoUsuario solicitante, LocalDate dataSolicitacao,
                                    String motivo) {
        return comCancelamento(contrato, solicitante, dataSolicitacao, dataSolicitacao.plusDays(30), motivo);
    }

    public Contrato comCancelamento(Contrato contrato, TipoUsuario solicitante, LocalDate dataSolicitacao,
                                    LocalDate dataEncerramentoEfetivo, String motivo) {
        Contrato c = contratos.findById(contrato.getId()).orElseThrow();
        c.setDataSolicitacaoCancelamento(dataSolicitacao);
        c.setDataEncerramentoEfetivo(dataEncerramentoEfetivo);
        c.setSolicitanteCancelamento(solicitante);
        c.setMotivoCancelamento(motivo);
        return contratos.save(c);
    }

    /** Mensalidade da competência (qualquer dia do mês; é normalizada para o dia 1), valor = valor do contrato. */
    public Mensalidade mensalidade(Contrato contrato, LocalDate competencia, StatusMensalidade status) {
        LocalDate c = competencia.withDayOfMonth(1);
        return mensalidade(contrato, c, c.withDayOfMonth(10), status,
                status == StatusMensalidade.PAGA ? c.withDayOfMonth(10) : null);
    }

    public Mensalidade mensalidade(Contrato contrato, LocalDate competencia, LocalDate vencimento,
                                   StatusMensalidade status, LocalDate dataPagamento) {
        Mensalidade m = new Mensalidade();
        m.setContrato(contrato);
        m.setCompetencia(competencia.withDayOfMonth(1));
        m.setValor(contrato.getValorMensal());
        m.setVencimento(vencimento);
        m.setStatus(status);
        m.setDataPagamento(dataPagamento);
        return mensalidades.save(m);
    }
}
