package br.com.smartvan.cadastro.repository;

import br.com.smartvan.model.AlunoEndereco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AlunoEnderecoBuscaRepository extends JpaRepository<AlunoEndereco, AlunoEndereco.AlunoEnderecoId> {

    @Query("select ae from AlunoEndereco ae join fetch ae.endereco where ae.aluno.id in :alunoIds and ae.principal = true")
    List<AlunoEndereco> principaisDosAlunos(@Param("alunoIds") Collection<Long> alunoIds);
}
