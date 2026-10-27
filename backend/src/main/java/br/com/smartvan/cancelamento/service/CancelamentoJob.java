package br.com.smartvan.cancelamento.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CancelamentoJob {

    private static final Logger log = LoggerFactory.getLogger(CancelamentoJob.class);

    private final CancelamentoService service;

    public CancelamentoJob(CancelamentoService service) {
        this.service = service;
    }

    @Scheduled(cron = "${smartvan.agendador.cancelamentos.cron}", zone = "America/Sao_Paulo")
    public void encerrarVencidos() {
        for (Long id : service.idsVencidos()) {
            try {
                service.encerrar(id);
            } catch (RuntimeException e) {
                log.error("Falha ao encerrar o contrato {}", id, e);
            }
        }
    }
}
