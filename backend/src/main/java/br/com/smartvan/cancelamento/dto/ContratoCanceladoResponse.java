package br.com.smartvan.cancelamento.dto;

import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.TipoUsuario;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.model.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Espelho do objeto {@code Contrato} da seção 2 do contrato da API. Provisório: o orquestrador troca pelo
 * ContratoResponse oficial da contratação no merge.
 */
public record ContratoCanceladoResponse(Long id, StatusContrato status, BigDecimal valorMensal, LocalDate dataInicio,
                                        AlunoResumo aluno, UsuarioResumo responsavel, UsuarioResumo motorista,
                                        EscolaResumo escola, CancelamentoInfo cancelamento) {

    public record AlunoResumo(Long id, String nome) {}
    public record UsuarioResumo(Long id, String nome, String telefone) {}
    public record EscolaResumo(Long id, String nome) {}
    public record CancelamentoInfo(LocalDate dataSolicitacao, LocalDate dataEncerramentoEfetivo,
                                   TipoUsuario solicitante, String motivo) {}

    public static ContratoCanceladoResponse de(Contrato c) {
        var escola = c.getAluno().getEscola() == null ? null
                : new EscolaResumo(c.getAluno().getEscola().getId(), c.getAluno().getEscola().getNome());
        var canc = c.getDataSolicitacaoCancelamento() == null ? null
                : new CancelamentoInfo(c.getDataSolicitacaoCancelamento(), c.getDataEncerramentoEfetivo(),
                        c.getSolicitanteCancelamento(), c.getMotivoCancelamento());
        return new ContratoCanceladoResponse(c.getId(), c.getStatus(), c.getValorMensal(), c.getDataInicio(),
                new AlunoResumo(c.getAluno().getId(), c.getAluno().getNome()),
                resumo(c.getResponsavel()), resumo(c.getMotorista()), escola, canc);
    }

    private static UsuarioResumo resumo(Usuario u) {
        return new UsuarioResumo(u.getId(), u.getNome(), u.getTelefone());
    }
}
