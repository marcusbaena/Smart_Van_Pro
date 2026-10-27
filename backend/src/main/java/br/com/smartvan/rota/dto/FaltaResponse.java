package br.com.smartvan.rota.dto;

import br.com.smartvan.model.Falta;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FaltaResponse(Long id, LocalDate data, String motivo) {
    public static FaltaResponse de(Falta f) {
        return new FaltaResponse(f.getId(), f.getDataFalta(), f.getMotivo());
    }
}
