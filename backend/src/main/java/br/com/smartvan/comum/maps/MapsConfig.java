package br.com.smartvan.comum.maps;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** O módulo de rota registra a implementação do Google quando houver chave; senão vale esta. */
@Configuration
public class MapsConfig {

    @Bean
    @ConditionalOnMissingBean(MapsProvider.class)
    public MapsProvider mapsIndisponivel() {
        return new MapsIndisponivel();
    }
}
