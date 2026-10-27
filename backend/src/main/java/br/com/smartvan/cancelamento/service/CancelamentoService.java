package br.com.smartvan.cancelamento.service;

import br.com.smartvan.contratacao.dto.ContratoMapper;
import br.com.smartvan.contratacao.dto.ContratoResponse;
import br.com.smartvan.cancelamento.repository.CancelamentoRepository;
import br.com.smartvan.financeiro.service.MensalidadeConsultaService;
import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.comum.seguranca.UsuarioLogado;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.notificacao.NotificacaoService;
import br.com.smartvan.notificacao.TipoNotificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class CancelamentoService {

    static final int AVISO_PREVIO_DIAS = 30;

    private final CancelamentoRepository repository;
    private final MensalidadeConsultaService mensalidades;
    private final NotificacaoService notificacaoService;
    private final Clock clock;

    public CancelamentoService(CancelamentoRepository repository, MensalidadeConsultaService mensalidades, NotificacaoService notificacaoService, Clock clock) {
        this.repository = repository;
        this.mensalidades = mensalidades;
        this.notificacaoService = notificacaoService;
        this.clock = clock;
    }

    @Transactional
    public ContratoResponse solicitar(Long contratoId, String motivo) {
        UsuarioLogado eu = UsuarioLogado.atual();
        Contrato c = repository.findById(contratoId).orElseThrow(() -> RegraNegocioException.naoEncontrado("Contrato"));
        boolean ehResponsavel = eu.ehResponsavel() && c.getResponsavel().getId().equals(eu.id());
        boolean ehMotorista = eu.ehMotorista() && c.getMotorista().getId().equals(eu.id());
        if (!ehResponsavel && !ehMotorista) {
            throw RegraNegocioException.semPermissao();
        }
        if (c.getStatus() != StatusContrato.ATIVO) {
            throw RegraNegocioException.conflito("CONTRATO_NAO_ATIVO", "Só é possível cancelar um contrato ativo.");
        }
        if (c.getDataSolicitacaoCancelamento() != null) {
            throw RegraNegocioException.conflito("CANCELAMENTO_JA_SOLICITADO", "O cancelamento já foi solicitado.");
        }
        LocalDate hoje = LocalDate.now(clock);
        c.setDataSolicitacaoCancelamento(hoje);
        c.setDataEncerramentoEfetivo(hoje.plusDays(AVISO_PREVIO_DIAS));
        c.setSolicitanteCancelamento(eu.tipo());
        c.setMotivoCancelamento(motivo);

        Long outraParte = ehResponsavel ? c.getMotorista().getId() : c.getResponsavel().getId();
        notificacaoService.notificar(outraParte, TipoNotificacao.CANCELAMENTO_SOLICITADO,
                "Cancelamento solicitado",
                "O contrato de " + c.getAluno().getNome() + " será encerrado em " + c.getDataEncerramentoEfetivo()
                        + ". Motivo: " + motivo, c.getId());
        return ContratoMapper.de(c);
    }

    /** Ids dos contratos ATIVOS cujo aviso prévio venceu. O job encerra um a um (cada um em sua transação). */
    public List<Long> idsVencidos() {
        return repository.idsParaEncerrar(StatusContrato.ATIVO, LocalDate.now(clock));
    }

    @Transactional
    public void encerrar(Long contratoId) {
        Contrato c = repository.findById(contratoId).orElseThrow();
        if (c.getStatus() != StatusContrato.ATIVO) {
            return;
        }
        c.setStatus(StatusContrato.ENCERRADO);
        boolean pendencia = mensalidades.existePendenteOuAtrasada(c.getId());
        TipoNotificacao tipo = pendencia ? TipoNotificacao.CANCELAMENTO_COM_PENDENCIA : TipoNotificacao.CONTRATO_ENCERRADO;
        String titulo = pendencia ? "Contrato encerrado com pendência" : "Contrato encerrado";
        String msg = "O contrato de " + c.getAluno().getNome() + " foi encerrado."
                + (pendencia ? " Existem mensalidades pendentes ou atrasadas a regularizar." : "");
        notificacaoService.notificar(c.getResponsavel().getId(), tipo, titulo, msg, c.getId());
        notificacaoService.notificar(c.getMotorista().getId(), tipo, titulo, msg, c.getId());
    }
}
