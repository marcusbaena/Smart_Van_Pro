package br.com.smartvan.financeiro.service;

import br.com.smartvan.enums.StatusMensalidade;
import br.com.smartvan.financeiro.repository.MensalidadeBuscaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Serviço exposto a outros módulos (cancelamento). */
@Service
public class MensalidadeConsultaService {

    private final MensalidadeBuscaRepository repository;

    public MensalidadeConsultaService(MensalidadeBuscaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean existePendenteOuAtrasada(Long contratoId) {
        return repository.existsByContratoIdAndStatusIn(contratoId,
                List.of(StatusMensalidade.PENDENTE, StatusMensalidade.ATRASADA));
    }
}
