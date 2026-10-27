package br.com.smartvan.contratacao.service;

import br.com.smartvan.cadastro.dto.EscolaResumo;
import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.comum.evento.ContratoAtivadoEvent;
import br.com.smartvan.comum.seguranca.UsuarioLogado;
import br.com.smartvan.contratacao.dto.ContratoMapper;
import br.com.smartvan.contratacao.dto.ContratoResponse;
import br.com.smartvan.contratacao.dto.MotoristaBusca;
import br.com.smartvan.contratacao.dto.PropostaRequest;
import br.com.smartvan.contratacao.repository.CapacidadeRepository;
import br.com.smartvan.contratacao.repository.ContratoBuscaRepository;
import br.com.smartvan.contratacao.repository.MotoristaBuscaRepository;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.StatusMotorista;
import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.model.Escola;
import br.com.smartvan.model.Motorista;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.model.Veiculo;
import br.com.smartvan.notificacao.NotificacaoService;
import br.com.smartvan.notificacao.TipoNotificacao;
import br.com.smartvan.repositorio.AlunoRepository;
import br.com.smartvan.repositorio.ResponsavelRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ContratacaoService {

    private final MotoristaBuscaRepository motoristas;
    private final ContratoBuscaRepository contratos;
    private final CapacidadeRepository capacidade;
    private final AlunoRepository alunos;
    private final ResponsavelRepository responsaveis;
    private final NotificacaoService notificacoes;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public ContratacaoService(MotoristaBuscaRepository motoristas, ContratoBuscaRepository contratos,
                              CapacidadeRepository capacidade, AlunoRepository alunos,
                              ResponsavelRepository responsaveis, NotificacaoService notificacoes,
                              ApplicationEventPublisher eventos, Clock clock) {
        this.motoristas = motoristas;
        this.contratos = contratos;
        this.capacidade = capacidade;
        this.alunos = alunos;
        this.responsaveis = responsaveis;
        this.notificacoes = notificacoes;
        this.eventos = eventos;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ busca

    @Transactional(readOnly = true)
    public List<MotoristaBusca> buscar(Long responsavelId, Long escolaId, String cidade, String nome) {
        exigirAluno(responsavelId);
        String nomePadrao = vazio(nome) ? "%" : "%" + nome.trim().toLowerCase(Locale.ROOT) + "%";
        boolean semCidade = vazio(cidade);
        String cidadePadrao = semCidade ? "%" : "%" + cidade.trim().toLowerCase(Locale.ROOT) + "%";
        return motoristas.buscar(nomePadrao, escolaId == null, escolaId == null ? -1L : escolaId,
                        cidadePadrao, semCidade)
                .stream().map(this::paraBusca).toList();
    }

    @Transactional(readOnly = true)
    public MotoristaBusca perfil(Long responsavelId, Long motoristaId) {
        exigirAluno(responsavelId);
        Motorista m = motoristas.findById(motoristaId)
                .filter(x -> x.getStatusValidacao() == StatusMotorista.VALIDADO)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Motorista"));
        return paraBusca(m);
    }

    private void exigirAluno(Long responsavelId) {
        if (capacidade.contarAlunosDoResponsavel(responsavelId) == 0) {
            throw RegraNegocioException.regra("ALUNO_OBRIGATORIO",
                    "Cadastre ao menos um aluno antes de buscar motoristas.");
        }
    }

    private MotoristaBusca paraBusca(Motorista m) {
        Veiculo v = capacidade.doMotoristaPorCapacidade(m.getId()).stream().findFirst().orElse(null);
        List<Escola> escolas = m.getEscolasAtendidas().stream()
                .sorted(Comparator.comparing(Escola::getNome).thenComparing(Escola::getId)).toList();
        List<String> cidades = escolas.stream().map(e -> e.getEndereco().getCidade()).distinct().sorted().toList();
        return new MotoristaBusca(m.getId(), m.getNome(), m.getTelefone(),
                v == null ? null : new MotoristaBusca.VeiculoResumo(v.getModelo(), v.getTipo().name(), v.getCapacidade()),
                escolas.stream().map(EscolaResumo::de).toList(), cidades, vagasRestantes(m.getId(), v));
    }

    private int vagasRestantes(Long motoristaId, Veiculo maior) {
        int cap = maior == null ? 0 : maior.getCapacidade();
        long ativos = contratos.countByMotoristaIdAndStatus(motoristaId, StatusContrato.ATIVO);
        return (int) Math.max(0, cap - ativos);
    }

    private int vagasRestantes(Long motoristaId) {
        Veiculo v = capacidade.doMotoristaPorCapacidade(motoristaId).stream().findFirst().orElse(null);
        return vagasRestantes(motoristaId, v);
    }

    // --------------------------------------------------------------- proposta

    @Transactional
    public ContratoResponse propor(Long responsavelId, PropostaRequest req) {
        Aluno aluno = alunos.findById(req.alunoId()).orElseThrow(() -> RegraNegocioException.naoEncontrado("Aluno"));
        if (!capacidade.responsavelTemAluno(responsavelId, aluno.getId())) {
            throw RegraNegocioException.semPermissao();
        }
        Motorista motorista = motoristas.findById(req.motoristaId())
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Motorista"));
        if (motorista.getStatusValidacao() != StatusMotorista.VALIDADO) {
            throw RegraNegocioException.regra("MOTORISTA_NAO_VALIDADO", "O motorista ainda não foi validado.");
        }
        if (vagasRestantes(motorista.getId()) <= 0) {
            throw RegraNegocioException.conflito("SEM_VAGAS", "O motorista não tem vagas disponíveis.");
        }
        if (contratos.existsByAlunoIdAndMotoristaIdAndStatus(aluno.getId(), motorista.getId(), StatusContrato.PENDENTE)) {
            throw RegraNegocioException.conflito("PROPOSTA_DUPLICADA",
                    "Já existe uma proposta pendente para este aluno com este motorista.");
        }
        if (contratos.existsByAlunoIdAndStatus(aluno.getId(), StatusContrato.ATIVO)) {
            throw RegraNegocioException.conflito("ALUNO_JA_TEM_CONTRATO", "O aluno já possui um contrato ativo.");
        }
        Responsavel responsavel = responsaveis.findById(responsavelId)
                .orElseThrow(RegraNegocioException::semPermissao);

        Contrato c = new Contrato();
        c.setResponsavel(responsavel);
        c.setMotorista(motorista);
        c.setAluno(aluno);
        c.setValorMensal(req.valorMensal());
        c.setDataInicio(LocalDate.now(clock));
        c.setStatus(StatusContrato.PENDENTE);
        c = contratos.save(c);

        notificacoes.notificar(motorista.getId(), TipoNotificacao.PROPOSTA_RECEBIDA, "Nova proposta de contrato",
                responsavel.getNome() + " enviou uma proposta para o aluno " + aluno.getNome() + ".", c.getId());
        return ContratoMapper.de(c);
    }

    @Transactional
    public ContratoResponse aceitar(Long motoristaId, Long contratoId) {
        Contrato c = doMotorista(motoristaId, contratoId);
        exigirPendente(c);
        if (vagasRestantes(motoristaId) <= 0) {
            throw RegraNegocioException.conflito("SEM_VAGAS", "O motorista não tem vagas disponíveis.");
        }
        if (contratos.existsByAlunoIdAndStatus(c.getAluno().getId(), StatusContrato.ATIVO)) {
            throw RegraNegocioException.conflito("ALUNO_JA_TEM_CONTRATO", "O aluno já possui um contrato ativo.");
        }
        c.setStatus(StatusContrato.ATIVO);
        c.setDataInicio(LocalDate.now(clock));
        c = contratos.saveAndFlush(c);

        notificacoes.notificar(c.getResponsavel().getId(), TipoNotificacao.PROPOSTA_ACEITA, "Proposta aceita",
                c.getMotorista().getNome() + " aceitou a proposta para " + c.getAluno().getNome() + ".", c.getId());
        eventos.publishEvent(new ContratoAtivadoEvent(c.getId()));
        return ContratoMapper.de(c);
    }

    @Transactional
    public ContratoResponse recusar(Long motoristaId, Long contratoId) {
        Contrato c = doMotorista(motoristaId, contratoId);
        exigirPendente(c);
        c.setStatus(StatusContrato.RECUSADO);
        c = contratos.save(c);
        notificacoes.notificar(c.getResponsavel().getId(), TipoNotificacao.PROPOSTA_RECUSADA, "Proposta recusada",
                c.getMotorista().getNome() + " recusou a proposta para " + c.getAluno().getNome() + ".", c.getId());
        return ContratoMapper.de(c);
    }

    private Contrato doMotorista(Long motoristaId, Long contratoId) {
        Contrato c = contratos.carregar(contratoId).orElseThrow(() -> RegraNegocioException.naoEncontrado("Contrato"));
        if (!c.getMotorista().getId().equals(motoristaId)) {
            throw RegraNegocioException.semPermissao();
        }
        return c;
    }

    private void exigirPendente(Contrato c) {
        if (c.getStatus() != StatusContrato.PENDENTE) {
            throw RegraNegocioException.conflito("CONTRATO_NAO_PENDENTE", "O contrato não está pendente.");
        }
    }

    // ------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<ContratoResponse> listar(UsuarioLogado eu, StatusContrato status) {
        return contratos.doUsuario(eu.id(), status == null, status == null ? StatusContrato.PENDENTE : status)
                .stream().map(ContratoMapper::de).toList();
    }

    @Transactional(readOnly = true)
    public ContratoResponse obter(UsuarioLogado eu, Long id) {
        Contrato c = contratos.carregar(id).orElseThrow(() -> RegraNegocioException.naoEncontrado("Contrato"));
        if (!c.getResponsavel().getId().equals(eu.id()) && !c.getMotorista().getId().equals(eu.id())) {
            throw RegraNegocioException.semPermissao();
        }
        return ContratoMapper.de(c);
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
