package br.com.smartvan.rota.repository;

import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.model.ResponsavelAluno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RotaContratoRepository extends JpaRepository<Contrato, Long> {

    @Query("select c.aluno from Contrato c where c.motorista.id = :motoristaId "
            + "and c.status = br.com.smartvan.enums.StatusContrato.ATIVO and c.aluno.escola.id = :escolaId "
            + "order by c.aluno.nome, c.aluno.id")
    List<Aluno> alunosAtivosNaEscola(@Param("motoristaId") Long motoristaId, @Param("escolaId") Long escolaId);

    @Query("select ra from ResponsavelAluno ra where ra.aluno.id in :alunoIds "
            + "and ra.statusVinculo = br.com.smartvan.enums.StatusVinculo.ATIVO")
    List<ResponsavelAluno> vinculosAtivos(@Param("alunoIds") Collection<Long> alunoIds);

    @Query("select ra from ResponsavelAluno ra where ra.responsavel.id = :responsavelId and ra.aluno.id = :alunoId "
            + "and ra.statusVinculo = br.com.smartvan.enums.StatusVinculo.ATIVO")
    List<ResponsavelAluno> vinculoAtivo(@Param("responsavelId") Long responsavelId, @Param("alunoId") Long alunoId);
}
