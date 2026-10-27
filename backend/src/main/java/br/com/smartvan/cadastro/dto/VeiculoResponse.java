package br.com.smartvan.cadastro.dto;

import br.com.smartvan.enums.TipoVeiculo;
import br.com.smartvan.model.Veiculo;

public record VeiculoResponse(Long id, String placa, String modelo, int ano, int capacidade, TipoVeiculo tipo) {

    public static VeiculoResponse de(Veiculo v) {
        return new VeiculoResponse(v.getId(), v.getPlaca(), v.getModelo(), v.getAno(), v.getCapacidade(), v.getTipo());
    }
}
