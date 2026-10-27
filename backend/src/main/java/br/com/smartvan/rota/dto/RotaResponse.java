package br.com.smartvan.rota.dto;

import br.com.smartvan.cadastro.dto.EnderecoResponse;
import br.com.smartvan.cadastro.dto.EscolaResumo;
import br.com.smartvan.enums.StatusRota;
import br.com.smartvan.enums.TipoParada;
import br.com.smartvan.enums.TipoRota;
import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.ParadaRota;
import br.com.smartvan.model.Rota;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RotaResponse(Long id, LocalDate data, TipoRota tipo, StatusRota status, EscolaResumo escola,
                           VeiculoResumo veiculo, boolean otimizada, Integer tempoEstimadoMinutos,
                           BigDecimal distanciaKm,
                           @JsonFormat(pattern = "HH:mm") LocalTime horarioInicio,
                           @JsonFormat(pattern = "HH:mm") LocalTime horarioFim,
                           String polyline, List<ParadaResponse> paradas) {

    public record VeiculoResumo(Long id, String placa, String modelo) {
    }

    public record AlunoResumo(Long id, String nome) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParadaResponse(int ordem, TipoParada tipoParada, AlunoResumo aluno, EnderecoResponse endereco,
                                 @JsonFormat(pattern = "HH:mm") LocalTime horarioPrevisto) {
        static ParadaResponse de(ParadaRota p) {
            Aluno a = p.getAluno();
            return new ParadaResponse(p.getOrdem(), p.getTipoParada(),
                    a == null ? null : new AlunoResumo(a.getId(), a.getNome()),
                    EnderecoResponse.de(p.getEndereco()), p.getHorarioPrevisto());
        }
    }

    /** Precisa de sessão aberta (lazy). */
    public static RotaResponse de(Rota r) {
        return new RotaResponse(r.getId(), r.getDataRota(), r.getTipo(), r.getStatus(),
                EscolaResumo.de(r.getEscola()),
                new VeiculoResumo(r.getVeiculo().getId(), r.getVeiculo().getPlaca(), r.getVeiculo().getModelo()),
                r.isOtimizada(), r.getTempoEstimadoMinutos(), r.getDistanciaKm(), r.getHorarioInicio(),
                r.getHorarioFim(), r.getPolyline(), r.getParadas().stream().map(ParadaResponse::de).toList());
    }
}
