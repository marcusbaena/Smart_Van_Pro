package br.com.smartvan.comum.erro;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(int status, String codigo, String mensagem, Map<String, String> campos, Instant timestamp) {

    public static ErroResponse de(int status, String codigo, String mensagem) {
        return new ErroResponse(status, codigo, mensagem, null, Instant.now());
    }
}
