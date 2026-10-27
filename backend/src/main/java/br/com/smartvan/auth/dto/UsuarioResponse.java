package br.com.smartvan.auth.dto;

import br.com.smartvan.enums.StatusConta;
import br.com.smartvan.enums.StatusMotorista;
import br.com.smartvan.enums.TipoUsuario;
import br.com.smartvan.model.Motorista;
import br.com.smartvan.model.Usuario;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponse(Long id, String nome, String email, String cpf, String telefone, TipoUsuario tipo,
                              StatusConta status, StatusMotorista statusValidacao) {

    public static UsuarioResponse de(Usuario u) {
        StatusMotorista validacao = u instanceof Motorista m ? m.getStatusValidacao() : null;
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getCpf(), u.getTelefone(), u.getTipo(),
                u.getStatus(), validacao);
    }
}
