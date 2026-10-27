package br.com.smartvan.contratacao.dto;

import br.com.smartvan.model.Aluno;

public record AlunoResumo(Long id, String nome) {

    public static AlunoResumo de(Aluno a) {
        return new AlunoResumo(a.getId(), a.getNome());
    }
}
