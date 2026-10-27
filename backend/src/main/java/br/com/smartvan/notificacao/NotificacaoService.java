package br.com.smartvan.notificacao;

import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.model.Usuario;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Notificações dentro do app (decisão Q4). Todos os módulos usam {@link #notificar}. */
@Service
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository repository, EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public void notificar(Long usuarioId, TipoNotificacao tipo, String titulo, String mensagem, Long referenciaId) {
        Notificacao n = new Notificacao();
        n.setUsuario(entityManager.getReference(Usuario.class, usuarioId));
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setMensagem(mensagem);
        n.setReferenciaId(referenciaId);
        n.setLida(false);
        n.setDataCriacao(clock.instant());
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listar(Long usuarioId, Boolean lidas) {
        List<Notificacao> lista = lidas == null
                ? repository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)
                : repository.findByUsuarioIdAndLidaOrderByDataCriacaoDesc(usuarioId, lidas);
        return lista.stream().map(NotificacaoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Long usuarioId) {
        return repository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    @Transactional
    public void marcarComoLida(Long usuarioId, Long notificacaoId) {
        Notificacao n = repository.findById(notificacaoId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Notificação"));
        if (!n.getUsuario().getId().equals(usuarioId)) {
            throw RegraNegocioException.semPermissao();
        }
        n.setLida(true);
    }
}
