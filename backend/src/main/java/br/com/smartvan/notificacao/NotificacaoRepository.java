package br.com.smartvan.notificacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByUsuarioIdOrderByDataCriacaoDesc(Long usuarioId);

    List<Notificacao> findByUsuarioIdAndLidaOrderByDataCriacaoDesc(Long usuarioId, boolean lida);

    long countByUsuarioIdAndLidaFalse(Long usuarioId);
}
