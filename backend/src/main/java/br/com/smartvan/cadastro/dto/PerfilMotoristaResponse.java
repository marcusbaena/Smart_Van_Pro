package br.com.smartvan.cadastro.dto;

import br.com.smartvan.auth.dto.UsuarioResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerfilMotoristaResponse(UsuarioResponse usuario, EnderecoResponse enderecoPartida,
                                      List<VeiculoResponse> veiculos, List<EscolaResumo> escolas) {
}
