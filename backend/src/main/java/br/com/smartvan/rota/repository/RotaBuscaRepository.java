package br.com.smartvan.rota.repository;

import br.com.smartvan.enums.StatusRota;
import br.com.smartvan.enums.TipoRota;
import br.com.smartvan.model.Rota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RotaBuscaRepository extends JpaRepository<Rota, Long> {

    List<Rota> findByMotoristaIdAndDataRotaAndTipoAndEscolaIdAndStatus(
            Long motoristaId, LocalDate data, TipoRota tipo, Long escolaId, StatusRota status);

    List<Rota> findByMotoristaIdAndDataRotaOrderByHorarioInicioAscIdAsc(Long motoristaId, LocalDate data);
}
