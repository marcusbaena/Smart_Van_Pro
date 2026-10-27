package br.com.smartvan.cadastro.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EscolasAtendidasRequest(@NotNull List<@NotNull Long> escolaIds) {
}
