package br.com.smartvan.repositorio;

import br.com.smartvan.model.ResponsavelAluno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Compartilhado (Wave 0). Não editar: consultas específicas vão num repository do próprio módulo. */
public interface ResponsavelAlunoRepository extends JpaRepository<ResponsavelAluno, ResponsavelAluno.ResponsavelAlunoId> {

    @Query("select count(ra) > 0 from ResponsavelAluno ra where ra.responsavel.id = :responsavelId and ra.aluno.id = :alunoId")
    boolean alunoPertenceAoResponsavel(Long responsavelId, Long alunoId);
}
