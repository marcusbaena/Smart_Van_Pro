package br.com.smartvan.cancelamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelamentoRequest(@NotBlank @Size(min = 5, max = 500) String motivo) {
}
