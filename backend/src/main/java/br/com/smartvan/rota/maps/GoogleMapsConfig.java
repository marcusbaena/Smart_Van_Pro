package br.com.smartvan.rota.maps;

import br.com.smartvan.comum.maps.MapsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Registra o Google só quando smartvan.google-maps.api-key (GOOGLE_MAPS_API_KEY) não está vazio.
 * É {@code @Primary}: o plano B de comum/maps usa @ConditionalOnMissingBean, que depende da ordem de processamento
 * das configurações, então os dois podem coexistir e o Google vence.
 */
@Configuration
public class GoogleMapsConfig {

    @Bean
    @Primary
    @ConditionalOnExpression("!'${smartvan.google-maps.api-key:}'.trim().isEmpty()")
    public MapsProvider googleMapsProvider(@Value("${smartvan.google-maps.api-key}") String chave) {
        return new GoogleMapsProvider(chave.trim());
    }
}
