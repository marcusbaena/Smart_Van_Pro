package br.com.smartvan.rota.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FaltaRequest(@NotNull LocalDate data, String motivo) {
}
