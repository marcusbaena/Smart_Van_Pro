package br.com.smartvan.contratacao.repository;

import br.com.smartvan.enums.StatusVinculo;
import br.com.smartvan.model.Veiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Consultas de apoio: veículos do motorista e vínculos responsável-aluno. */
public interface CapacidadeRepository extends JpaRepository<Veiculo, Long> {

    /** Veículos do motorista, o de maior capacidade primeiro. */
    @Query("select v from Veiculo v where v.motorista.id = :motoristaId order by v.capacidade desc, v.id")
    List<Veiculo> doMotoristaPorCapacidade(@Param("motoristaId") Long motoristaId);

    @Query("select count(ra) from ResponsavelAluno ra where ra.responsavel.id = :responsavelId "
            + "and ra.aluno.id = :alunoId and ra.statusVinculo = :status")
    long contarVinculos(@Param("responsavelId") Long responsavelId, @Param("alunoId") Long alunoId,
                        @Param("status") StatusVinculo status);

    default boolean responsavelTemAluno(Long responsavelId, Long alunoId) {
        return contarVinculos(responsavelId, alunoId, StatusVinculo.ATIVO) > 0;
    }

    @Query("select count(ra) from ResponsavelAluno ra where ra.responsavel.id = :responsavelId "
            + "and ra.statusVinculo = br.com.smartvan.enums.StatusVinculo.ATIVO")
    long contarAlunosDoResponsavel(@Param("responsavelId") Long responsavelId);
}
