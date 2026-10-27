package br.com.smartvan.cadastro.repository;

import br.com.smartvan.model.ResponsavelAluno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VinculoBuscaRepository extends JpaRepository<ResponsavelAluno, ResponsavelAluno.ResponsavelAlunoId> {

    @Query("select ra from ResponsavelAluno ra join fetch ra.aluno a left join fetch a.escola "
            + "where ra.responsavel.id = :responsavelId "
            + "and ra.statusVinculo = br.com.smartvan.enums.StatusVinculo.ATIVO order by a.nome, a.id")
    List<ResponsavelAluno> ativosDoResponsavel(@Param("responsavelId") Long responsavelId);
}
