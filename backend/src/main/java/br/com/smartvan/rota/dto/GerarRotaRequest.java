package br.com.smartvan.rota.dto;

import br.com.smartvan.enums.TipoRota;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record GerarRotaRequest(@NotNull LocalDate data, @NotNull TipoRota tipo, @NotNull Long escolaId,
                               Long veiculoId, LocalTime horarioSaida) {
}
