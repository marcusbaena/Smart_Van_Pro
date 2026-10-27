package br.com.smartvan.rota.repository;

import br.com.smartvan.model.Falta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RotaFaltaRepository extends JpaRepository<Falta, Long> {

    boolean existsByAlunoIdAndDataFalta(Long alunoId, LocalDate dataFalta);

    Optional<Falta> findByAlunoIdAndDataFalta(Long alunoId, LocalDate dataFalta);

    List<Falta> findByAlunoIdAndDataFaltaBetweenOrderByDataFalta(Long alunoId, LocalDate de, LocalDate ate);

    List<Falta> findByAlunoIdAndDataFaltaGreaterThanEqualOrderByDataFalta(Long alunoId, LocalDate de);

    @Query("select f.aluno.id from Falta f where f.dataFalta = :data and f.aluno.id in :alunoIds")
    List<Long> alunosComFalta(@Param("data") LocalDate data, @Param("alunoIds") Collection<Long> alunoIds);
}
