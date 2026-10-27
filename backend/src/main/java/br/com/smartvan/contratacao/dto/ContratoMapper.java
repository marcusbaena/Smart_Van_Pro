package br.com.smartvan.contratacao.dto;

import br.com.smartvan.cadastro.dto.EscolaResumo;
import br.com.smartvan.model.Contrato;

/**
 * Monta o objeto {@code Contrato} da API a partir da entidade. Público e estático para ser reutilizado por outros
 * módulos (ex.: cancelamento). Como aluno/responsável/motorista/escola são lazy, chame sempre dentro de uma transação
 * (ou com as associações já carregadas). O bloco {@code cancelamento} só vem preenchido quando há
 * {@code dataSolicitacaoCancelamento}; caso contrário é {@code null}.
 */
public final class ContratoMapper {

    private ContratoMapper() {
    }

    public static ContratoResponse de(Contrato c) {
        ContratoResponse.Cancelamento cancelamento = c.getDataSolicitacaoCancelamento() == null ? null
                : new ContratoResponse.Cancelamento(c.getDataSolicitacaoCancelamento(),
                        c.getDataEncerramentoEfetivo(), c.getSolicitanteCancelamento(), c.getMotivoCancelamento());
        EscolaResumo escola = c.getAluno().getEscola() == null ? null : EscolaResumo.de(c.getAluno().getEscola());
        return new ContratoResponse(c.getId(), c.getStatus(), c.getValorMensal(), c.getDataInicio(),
                AlunoResumo.de(c.getAluno()), UsuarioResumo.de(c.getResponsavel()),
                UsuarioResumo.de(c.getMotorista()), escola, cancelamento);
    }
}
