package br.com.smartvan.auth.dto;

import br.com.smartvan.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CadastroRequest(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 6, max = 100) String senha,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "deve ter 11 dígitos") String cpf,
        @NotBlank @Size(max = 20) String telefone,
        @NotNull @TipoCadastroPermitido TipoUsuario tipo) {
}
