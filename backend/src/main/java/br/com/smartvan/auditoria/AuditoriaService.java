package br.com.smartvan.auditoria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuditoriaService {

    private final AuditoriaRepository repository;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void registrar(Long usuarioId, String acao, String entidade, Long entidadeId, String detalhes) {
        Auditoria a = new Auditoria();
        a.setUsuarioId(usuarioId);
        a.setAcao(acao);
        a.setEntidade(entidade);
        a.setEntidadeId(entidadeId);
        a.setDetalhes(detalhes);
        a.setDataRegistro(clock.instant());
        repository.save(a);
    }
}
