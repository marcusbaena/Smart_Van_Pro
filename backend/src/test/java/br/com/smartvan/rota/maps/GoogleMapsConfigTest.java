package br.com.smartvan.rota.maps;

import br.com.smartvan.comum.maps.MapsConfig;
import br.com.smartvan.comum.maps.MapsIndisponivel;
import br.com.smartvan.comum.maps.MapsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** O Google só é registrado com chave; o MapsProvider injetado é sempre um só (o @Primary). */
class GoogleMapsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GoogleMapsConfig.class, MapsConfig.class);

    @Test
    void semChaveUsaOPlanoB() {
        runner.withPropertyValues("smartvan.google-maps.api-key=").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(GoogleMapsProvider.class);
            assertThat(ctx.getBean(MapsProvider.class)).isInstanceOf(MapsIndisponivel.class);
        });
    }

    @Test
    void chaveEmBrancoUsaOPlanoB() {
        runner.withPropertyValues("smartvan.google-maps.api-key=   ").run(ctx ->
                assertThat(ctx).doesNotHaveBean(GoogleMapsProvider.class));
    }

    @Test
    void comChaveUsaOGoogle() {
        runner.withPropertyValues("smartvan.google-maps.api-key=qualquer-chave").run(ctx ->
                assertThat(ctx.getBean(MapsProvider.class)).isInstanceOf(GoogleMapsProvider.class));
    }
}
