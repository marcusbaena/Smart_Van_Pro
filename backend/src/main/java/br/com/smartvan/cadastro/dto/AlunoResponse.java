package br.com.smartvan.cadastro.dto;

import br.com.smartvan.enums.Parentesco;
import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.Endereco;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlunoResponse(Long id, String nome, LocalDate dataNascimento, String serie, Parentesco parentesco,
                            EscolaResumo escola, EnderecoResponse endereco) {

    public static AlunoResponse de(Aluno a, Parentesco parentesco, Endereco endereco) {
        return new AlunoResponse(a.getId(), a.getNome(), a.getDataNascimento(), a.getSerie(), parentesco,
                a.getEscola() == null ? null : EscolaResumo.de(a.getEscola()),
                endereco == null ? null : EnderecoResponse.de(endereco));
    }
}
