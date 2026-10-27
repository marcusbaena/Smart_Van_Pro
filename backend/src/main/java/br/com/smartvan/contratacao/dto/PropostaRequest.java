package br.com.smartvan.contratacao.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PropostaRequest(
        @NotNull Long motoristaId,
        @NotNull Long alunoId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal valorMensal) {
}
