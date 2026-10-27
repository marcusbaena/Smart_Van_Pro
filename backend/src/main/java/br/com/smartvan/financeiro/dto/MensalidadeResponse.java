package br.com.smartvan.financeiro.dto;

import br.com.smartvan.enums.StatusMensalidade;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.model.Mensalidade;
import br.com.smartvan.model.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MensalidadeResponse(Long id, Long contratoId, LocalDate competencia, BigDecimal valor,
                                  LocalDate vencimento, StatusMensalidade status, LocalDate dataPagamento,
                                  AlunoResumo aluno, UsuarioResumo responsavel, UsuarioResumo motorista) {

    public record AlunoResumo(Long id, String nome) {
    }

    public record UsuarioResumo(Long id, String nome, String telefone) {
        static UsuarioResumo de(Usuario u) {
            return new UsuarioResumo(u.getId(), u.getNome(), u.getTelefone());
        }
    }

    /** Exige contrato, aluno, responsável e motorista já carregados (join fetch). */
    public static MensalidadeResponse de(Mensalidade m) {
        Contrato c = m.getContrato();
        return new MensalidadeResponse(m.getId(), c.getId(), m.getCompetencia(), m.getValor(), m.getVencimento(),
                m.getStatus(), m.getDataPagamento(),
                new AlunoResumo(c.getAluno().getId(), c.getAluno().getNome()),
                UsuarioResumo.de(c.getResponsavel()), UsuarioResumo.de(c.getMotorista()));
    }
}
