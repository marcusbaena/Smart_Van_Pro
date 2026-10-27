package br.com.smartvan.comum.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os @Scheduled. Nos testes fica desligado (smartvan.agendador.habilitado=false): chame os jobs direto. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "smartvan.agendador.habilitado", havingValue = "true", matchIfMissing = true)
public class AgendadorConfig {
}
