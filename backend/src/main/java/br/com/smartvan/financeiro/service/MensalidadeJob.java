package br.com.smartvan.financeiro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MensalidadeJob {

    private static final Logger log = LoggerFactory.getLogger(MensalidadeJob.class);

    private final MensalidadeService service;

    public MensalidadeJob(MensalidadeService service) {
        this.service = service;
    }

    @Scheduled(cron = "${smartvan.agendador.mensalidades.cron}", zone = "America/Sao_Paulo")
    public void executar() {
        gerarCompetenciaAtual();
        marcarAtrasadas();
    }

    /** Gera a competência atual para cada contrato ATIVO que ainda não tem. Idempotente. Devolve quantas criou. */
    public int gerarCompetenciaAtual() {
        int criadas = 0;
        for (Long contratoId : service.idsContratosAtivos()) {
            try {
                if (service.gerarParaContrato(contratoId)) {
                    criadas++;
                }
            } catch (DataIntegrityViolationException e) {
                log.debug("Mensalidade do contrato {} já criada em paralelo.", contratoId);
            } catch (RuntimeException e) {
                log.error("Falha ao gerar mensalidade do contrato {}", contratoId, e);
            }
        }
        return criadas;
    }

    /** PENDENTE vencida vira ATRASADA e notifica uma vez. Devolve quantas mudaram. */
    public int marcarAtrasadas() {
        return service.marcarAtrasadas();
    }
}
