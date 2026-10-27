package br.com.smartvan.cancelamento.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CancelamentoJob {

    private final CancelamentoService service;

    public CancelamentoJob(CancelamentoService service) {
        this.service = service;
    }

    @Scheduled(cron = "${smartvan.agendador.cancelamentos.cron}", zone = "America/Sao_Paulo")
    public void encerrarVencidos() {
        service.idsVencidos().forEach(service::encerrar);
    }
}
