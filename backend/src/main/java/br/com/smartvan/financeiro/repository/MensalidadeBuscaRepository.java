package br.com.smartvan.financeiro.repository;

import br.com.smartvan.enums.StatusContrato;
import br.com.smartvan.enums.StatusMensalidade;
import br.com.smartvan.model.Contrato;
import br.com.smartvan.model.Mensalidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Consultas do módulo financeiro; os join fetch evitam N+1 ao montar os resumos. */
public interface MensalidadeBuscaRepository extends JpaRepository<Mensalidade, Long> {

    @Query("select m from Mensalidade m join fetch m.contrato c join fetch c.aluno join fetch c.responsavel "
            + "join fetch c.motorista where c.id = :contratoId order by m.competencia desc")
    List<Mensalidade> porContrato(@Param("contratoId") Long contratoId);

    @Query("select m from Mensalidade m join fetch m.contrato c join fetch c.aluno join fetch c.responsavel "
            + "join fetch c.motorista where c.responsavel.id = :responsavelId order by m.competencia desc, m.id desc")
    List<Mensalidade> porResponsavel(@Param("responsavelId") Long responsavelId);

    @Query("select m from Mensalidade m join fetch m.contrato c join fetch c.aluno join fetch c.responsavel "
            + "join fetch c.motorista where c.motorista.id = :motoristaId order by m.competencia desc, m.id desc")
    List<Mensalidade> porMotorista(@Param("motoristaId") Long motoristaId);

    @Query("select m from Mensalidade m join fetch m.contrato c join fetch c.aluno join fetch c.responsavel "
            + "join fetch c.motorista where m.id = :id")
    Optional<Mensalidade> buscarCompleta(@Param("id") Long id);

    @Query("select m from Mensalidade m join fetch m.contrato c join fetch c.aluno join fetch c.responsavel "
            + "join fetch c.motorista where m.status = :status and m.vencimento < :hoje")
    List<Mensalidade> vencidas(@Param("status") StatusMensalidade status, @Param("hoje") LocalDate hoje);

    boolean existsByContratoIdAndCompetencia(Long contratoId, LocalDate competencia);

    boolean existsByContratoIdAndStatusIn(Long contratoId, Collection<StatusMensalidade> status);

    @Query("select c.id from Contrato c where c.status = :status")
    List<Long> idsContratosPorStatus(@Param("status") StatusContrato status);

    @Query("select c from Contrato c join fetch c.aluno join fetch c.responsavel join fetch c.motorista "
            + "where c.id = :id")
    Optional<Contrato> contratoCompleto(@Param("id") Long id);
}
