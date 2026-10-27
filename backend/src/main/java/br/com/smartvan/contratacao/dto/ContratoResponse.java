package br.com.smartvan.contratacao.dto;

import br.com.smartvan.cadastro.dto.EscolaResumo;
import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.TipoUsuario;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Objeto {@code Contrato} da seção 2 do contrato da API. Montado por {@link ContratoMapper#de}. {@code escola} e
 * {@code cancelamento} saem {@code null} quando não se aplicam.
 */
public record ContratoResponse(Long id, StatusContrato status, BigDecimal valorMensal, LocalDate dataInicio,
                               AlunoResumo aluno, UsuarioResumo responsavel, UsuarioResumo motorista,
                               EscolaResumo escola, Cancelamento cancelamento) {

    public record Cancelamento(LocalDate dataSolicitacao, LocalDate dataEncerramentoEfetivo,
                               TipoUsuario solicitante, String motivo) {
    }
}
