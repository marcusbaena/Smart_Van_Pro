package br.com.smartvan.financeiro.service;

import br.com.smartvan.auditoria.AuditoriaService;
import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.comum.evento.ContratoAtivadoEvent;
import br.com.smartvan.comum.seguranca.UsuarioLogado;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.StatusMensalidade;
import br.com.smartvan.financeiro.dto.MensalidadeResponse;
import br.com.smartvan.financeiro.repository.MensalidadeBuscaRepository;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.model.Mensalidade;
import br.com.smartvan.notificacao.NotificacaoService;
import br.com.smartvan.notificacao.TipoNotificacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class MensalidadeService {

    private final MensalidadeBuscaRepository repository;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final int diaVencimento;

    public MensalidadeService(MensalidadeBuscaRepository repository, NotificacaoService notificacoes,
                              AuditoriaService auditoria, Clock clock,
                              @Value("${smartvan.mensalidade.dia-vencimento:10}") int diaVencimento) {
        this.repository = repository;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.clock = clock;
        this.diaVencimento = diaVencimento;
    }

    /**
     * Roda na mesma transação de quem publica o evento (se houver), então a mensalidade nasce junto com o aceite;
     * fora de transação, abre a própria. Idempotente.
     */
    @EventListener
    @Transactional
    public void aoAtivarContrato(ContratoAtivadoEvent evento) {
        gerarParaContrato(evento.contratoId());
    }

    /** Gera a competência atual do contrato se ainda não existir. Devolve true se criou. */
    @Transactional
    public boolean gerarParaContrato(Long contratoId) {
        Contrato contrato = repository.contratoCompleto(contratoId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Contrato"));
        LocalDate hoje = LocalDate.now(clock);
        LocalDate competencia = hoje.withDayOfMonth(1);
        if (repository.existsByContratoIdAndCompetencia(contratoId, competencia)) {
            return false;
        }
        LocalDate vencimento = competencia.withDayOfMonth(diaVencimento);
        if (hoje.isAfter(vencimento)) {
            vencimento = hoje.plusDays(5);
        }
        Mensalidade m = new Mensalidade();
        m.setContrato(contrato);
        m.setCompetencia(competencia);
        m.setValor(contrato.getValorMensal());
        m.setVencimento(vencimento);
        m.setStatus(StatusMensalidade.PENDENTE);
        repository.saveAndFlush(m);
        notificacoes.notificar(contrato.getResponsavel().getId(), TipoNotificacao.MENSALIDADE_GERADA,
                "Mensalidade gerada",
                "A mensalidade de " + competencia.getMonthValue() + "/" + competencia.getYear() + " de "
                        + contrato.getAluno().getNome() + " vence em " + vencimento + ".",
                m.getId());
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> idsContratosAtivos() {
        return repository.idsContratosPorStatus(StatusContrato.ATIVO);
    }

    /** Marca ATRASADA as PENDENTE vencidas e notifica o responsável (só na virada de status). */
    @Transactional
    public int marcarAtrasadas() {
        LocalDate hoje = LocalDate.now(clock);
        List<Mensalidade> vencidas = repository.vencidas(StatusMensalidade.PENDENTE, hoje);
        for (Mensalidade m : vencidas) {
            m.setStatus(StatusMensalidade.ATRASADA);
            notificacoes.notificar(m.getContrato().getResponsavel().getId(), TipoNotificacao.MENSALIDADE_ATRASADA,
                    "Mensalidade em atraso",
                    "A mensalidade de " + m.getContrato().getAluno().getNome() + " com vencimento em "
                            + m.getVencimento() + " está em atraso.",
                    m.getId());
        }
        return vencidas.size();
    }

    @Transactional(readOnly = true)
    public List<MensalidadeResponse> listarPorContrato(Long contratoId) {
        UsuarioLogado eu = UsuarioLogado.atual();
        Contrato c = repository.contratoCompleto(contratoId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Contrato"));
        if (!c.getResponsavel().getId().equals(eu.id()) && !c.getMotorista().getId().equals(eu.id())) {
            throw RegraNegocioException.semPermissao();
        }
        return repository.porContrato(contratoId).stream().map(MensalidadeResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MensalidadeResponse> listarDoResponsavel(StatusMensalidade status) {
        return filtrar(repository.porResponsavel(UsuarioLogado.atual().id()), status, null);
    }

    @Transactional(readOnly = true)
    public List<MensalidadeResponse> listarCobrancas(StatusMensalidade status, String competencia) {
        LocalDate comp = null;
        if (competencia != null && !competencia.isBlank()) {
            try {
                comp = YearMonth.parse(competencia).atDay(1);
            } catch (DateTimeParseException e) {
                throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "VALIDACAO",
                        "Competência inválida, use YYYY-MM.");
            }
        }
        return filtrar(repository.porMotorista(UsuarioLogado.atual().id()), status, comp);
    }

    private List<MensalidadeResponse> filtrar(List<Mensalidade> lista, StatusMensalidade status, LocalDate comp) {
        return lista.stream()
                .filter(m -> status == null || m.getStatus() == status)
                .filter(m -> comp == null || m.getCompetencia().equals(comp))
                .map(MensalidadeResponse::de).toList();
    }

    @Transactional
    public MensalidadeResponse darBaixa(Long id, LocalDate dataPagamento) {
        UsuarioLogado eu = UsuarioLogado.atual();
        Mensalidade m = repository.buscarCompleta(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Mensalidade"));
        if (!m.getContrato().getMotorista().getId().equals(eu.id())) {
            throw RegraNegocioException.semPermissao();
        }
        if (m.getStatus() == StatusMensalidade.PAGA) {
            throw RegraNegocioException.conflito("MENSALIDADE_JA_PAGA", "Esta mensalidade já foi paga.");
        }
        LocalDate hoje = LocalDate.now(clock);
        LocalDate data = dataPagamento != null ? dataPagamento : hoje;
        if (data.isAfter(hoje)) {
            throw RegraNegocioException.regra("DATA_FUTURA", "A data de pagamento não pode ser futura.");
        }
        m.setStatus(StatusMensalidade.PAGA);
        m.setDataPagamento(data);
        auditoria.registrar(eu.id(), "BAIXA_MENSALIDADE", "Mensalidade", m.getId(),
                "Baixa manual em " + data + " (competência " + m.getCompetencia() + ", valor " + m.getValor() + ")");
        notificacoes.notificar(m.getContrato().getResponsavel().getId(), TipoNotificacao.MENSALIDADE_PAGA,
                "Pagamento registrado",
                "O pagamento da mensalidade de " + m.getContrato().getAluno().getNome() + " foi registrado em "
                        + data + ".",
                m.getId());
        return MensalidadeResponse.de(m);
    }
}
