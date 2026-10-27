package br.com.smartvan.cadastro.dto;

import br.com.smartvan.model.Escola;

public record EscolaResumo(Long id, String nome) {

    public static EscolaResumo de(Escola e) {
        return new EscolaResumo(e.getId(), e.getNome());
    }
}
