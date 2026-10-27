package br.com.smartvan.contratacao.dto;

import br.com.smartvan.model.Usuario;

public record UsuarioResumo(Long id, String nome, String telefone) {

    public static UsuarioResumo de(Usuario u) {
        return new UsuarioResumo(u.getId(), u.getNome(), u.getTelefone());
    }
}
