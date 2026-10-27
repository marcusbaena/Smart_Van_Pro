package br.com.smartvan.comum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "Hoje" no negócio é sempre em America/Sao_Paulo. Injete {@link Clock} e use LocalDate.now(clock).
 * Nos testes, substitua por Clock.fixed(...) com @TestConfiguration/@Primary.
 */
@Configuration
public class RelogioConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }
}
