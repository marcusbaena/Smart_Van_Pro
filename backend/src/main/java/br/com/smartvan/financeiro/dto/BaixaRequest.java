package br.com.smartvan.financeiro.dto;

import java.time.LocalDate;

public record BaixaRequest(LocalDate dataPagamento) {
}
