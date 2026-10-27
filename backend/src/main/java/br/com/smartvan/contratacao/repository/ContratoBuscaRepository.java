package br.com.smartvan.contratacao.repository;

import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.model.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContratoBuscaRepository extends JpaRepository<Contrato, Long> {

    long countByMotoristaIdAndStatus(Long motoristaId, StatusContrato status);

    boolean existsByAlunoIdAndStatus(Long alunoId, StatusContrato status);

    boolean existsByAlunoIdAndMotoristaIdAndStatus(Long alunoId, Long motoristaId, StatusContrato status);

    /** Contratos em que o usuário é parte (responsável ou motorista), mais recentes primeiro; status opcional. */
    @Query("select c from Contrato c where (c.responsavel.id = :usuarioId or c.motorista.id = :usuarioId) "
            + "and (:semStatus = true or c.status = :status) order by c.id desc")
    List<Contrato> doUsuario(@Param("usuarioId") Long usuarioId, @Param("semStatus") boolean semStatus,
                             @Param("status") StatusContrato status);

    @Query("select c from Contrato c where c.id = :id")
    Optional<Contrato> carregar(@Param("id") Long id);
}
