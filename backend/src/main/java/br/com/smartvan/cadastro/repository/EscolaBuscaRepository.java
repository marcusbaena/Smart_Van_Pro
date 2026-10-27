package br.com.smartvan.cadastro.repository;

import br.com.smartvan.model.Escola;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EscolaBuscaRepository extends JpaRepository<Escola, Long> {

    /** Padrões já em minúsculas e com % (ex.: "%pinheiros%"). */
    @Query("select e from Escola e join fetch e.endereco en "
            + "where lower(en.cidade) like :cidade and lower(e.nome) like :nome order by e.nome")
    List<Escola> buscar(@Param("cidade") String cidade, @Param("nome") String nome);
}
