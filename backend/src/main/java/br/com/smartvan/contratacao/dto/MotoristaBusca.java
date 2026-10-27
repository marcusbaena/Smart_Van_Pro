package br.com.smartvan.contratacao.dto;

import br.com.smartvan.cadastro.dto.EscolaResumo;

import java.util.List;

public record MotoristaBusca(Long id, String nome, String telefone, VeiculoResumo veiculo,
                             List<EscolaResumo> escolas, List<String> cidades, int vagasRestantes) {

    public record VeiculoResumo(String modelo, String tipo, int capacidade) {
    }
}
