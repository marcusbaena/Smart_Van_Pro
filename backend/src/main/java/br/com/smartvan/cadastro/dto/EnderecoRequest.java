package br.com.smartvan.cadastro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EnderecoRequest(
        @NotBlank @Size(max = 255) String logradouro,
        @NotBlank @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @NotBlank @Size(max = 100) String bairro,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "deve ter 2 letras maiúsculas") String uf,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "deve ter 8 dígitos") String cep,
        BigDecimal latitude,
        BigDecimal longitude) {
}
