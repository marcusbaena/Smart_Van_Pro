package br.com.smartvan.cancelamento.repository;

import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.model.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CancelamentoRepository extends JpaRepository<Contrato, Long> {

    @Query("select c.id from Contrato c where c.status = :status and c.dataEncerramentoEfetivo is not null "
            + "and c.dataEncerramentoEfetivo <= :hoje")
    List<Long> idsParaEncerrar(@Param("status") StatusContrato status, @Param("hoje") LocalDate hoje);

    /** Pendência = mensalidade PENDENTE ou ATRASADA do contrato. */
    @Query("select count(m) from Mensalidade m where m.contrato.id = :contratoId "
            + "and m.status in (br.com.smartvan.enums.StatusMensalidade.PENDENTE, "
            + "br.com.smartvan.enums.StatusMensalidade.ATRASADA)")
    long contarPendentesOuAtrasadas(@Param("contratoId") Long contratoId);
}
