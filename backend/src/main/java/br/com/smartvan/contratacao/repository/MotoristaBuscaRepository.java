package br.com.smartvan.contratacao.repository;

import br.com.smartvan.model.Motorista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MotoristaBuscaRepository extends JpaRepository<Motorista, Long> {

    /**
     * Busca de motoristas (UC05). A condição VALIDADO e os filtros ficam na consulta. Parâmetros nunca são nulos:
     * nomePadrao/cidadePadrao chegam como "%" (sem filtro) e semEscola/semCidade ligam e desligam os filtros.
     */
    @Query("select m from Motorista m where m.statusValidacao = br.com.smartvan.enums.StatusMotorista.VALIDADO "
            + "and lower(m.nome) like :nomePadrao "
            + "and (:semEscola = true or exists (select 1 from m.escolasAtendidas e where e.id = :escolaId)) "
            + "and (:semCidade = true or exists (select 1 from m.escolasAtendidas ec "
            + "where lower(ec.endereco.cidade) like :cidadePadrao)) "
            + "order by m.nome, m.id")
    List<Motorista> buscar(@Param("nomePadrao") String nomePadrao, @Param("semEscola") boolean semEscola,
                           @Param("escolaId") Long escolaId, @Param("cidadePadrao") String cidadePadrao,
                           @Param("semCidade") boolean semCidade);
}
