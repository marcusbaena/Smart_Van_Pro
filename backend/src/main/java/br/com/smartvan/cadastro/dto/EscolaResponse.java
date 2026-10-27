package br.com.smartvan.cadastro.dto;

import br.com.smartvan.model.Escola;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EscolaResponse(Long id, String nome, String telefone, EnderecoResponse endereco) {

    public static EscolaResponse de(Escola e) {
        return new EscolaResponse(e.getId(), e.getNome(), e.getTelefone(), EnderecoResponse.de(e.getEndereco()));
    }
}
