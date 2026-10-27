package br.com.smartvan.rota.maps;

import br.com.smartvan.comum.maps.Coordenada;
import br.com.smartvan.comum.maps.MapsIndisponivelException;
import br.com.smartvan.comum.maps.RotaOtimizada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Nunca chama o Google: o RestClient aponta para um MockRestServiceServer. */
class GoogleMapsProviderTest {

    private static final String CHAVE = "chave-de-teste-123";
    private MockRestServiceServer servidor;
    private GoogleMapsProvider provider;

    private static Coordenada c(String lat, String lng) {
        return new Coordenada(new BigDecimal(lat), new BigDecimal(lng));
    }

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleMapsProvider(builder.build(), CHAVE, GoogleMapsProvider.URL_ROTAS, GoogleMapsProvider.URL_GEOCODING);
    }

    @Test
    void otimizaRotaEInterpretaResposta() {
        servidor.expect(requestTo(GoogleMapsProvider.URL_ROTAS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-FieldMask", GoogleMapsProvider.FIELD_MASK))
                .andExpect(header("X-Goog-Api-Key", CHAVE))
                .andExpect(jsonPath("$.optimizeWaypointOrder").value(true))
                .andExpect(jsonPath("$.travelMode").value("DRIVE"))
                .andExpect(jsonPath("$.routingPreference").value("TRAFFIC_AWARE"))
                .andExpect(jsonPath("$.intermediates.length()").value(2))
                .andExpect(jsonPath("$.origin.location.latLng.latitude").value(-23.5))
                .andExpect(jsonPath("$.departureTime").isString())
                .andRespond(withSuccess("""
                        {"routes":[{
                          "optimizedIntermediateWaypointIndex":[1,0],
                          "legs":[{"duration":"300s","distanceMeters":1000},
                                  {"duration":"420s","distanceMeters":1500},
                                  {"duration":"180s","distanceMeters":700}],
                          "duration":"900s","distanceMeters":3200,
                          "polyline":{"encodedPolyline":"xyz_abc"}}]}
                        """, MediaType.APPLICATION_JSON));

        RotaOtimizada r = provider.otimizarRota(c("-23.5", "-46.6"),
                List.of(c("-23.51", "-46.61"), c("-23.52", "-46.62")), c("-23.53", "-46.63"),
                Instant.now().plusSeconds(3600));

        assertThat(r.ordemParadas()).containsExactly(1, 0);
        assertThat(r.segundosAteCadaParada()).containsExactly(300, 720);
        assertThat(r.segundosTotal()).isEqualTo(900);
        assertThat(r.metrosTotal()).isEqualTo(3200);
        assertThat(r.polyline()).isEqualTo("xyz_abc");
        servidor.verify();
    }

    @Test
    void saidaNoPassadoUsaAgoraMaisUmMinuto() {
        servidor.expect(requestTo(GoogleMapsProvider.URL_ROTAS))
                .andExpect(request -> {
                    String corpo = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    Instant partida = Instant.parse(corpo.replaceAll(".*\"departureTime\":\"([^\"]+)\".*", "$1"));
                    assertThat(partida).isAfter(Instant.now());
                })
                .andRespond(withSuccess("""
                        {"routes":[{"optimizedIntermediateWaypointIndex":[0],
                          "legs":[{"duration":"60s"},{"duration":"60s"}],"duration":"120s","distanceMeters":10}]}
                        """, MediaType.APPLICATION_JSON));

        RotaOtimizada r = provider.otimizarRota(c("-23.5", "-46.6"), List.of(c("-23.51", "-46.61")),
                c("-23.53", "-46.63"), Instant.parse("2020-01-01T10:00:00Z"));

        assertThat(r.polyline()).isNull();
        servidor.verify();
    }

    @Test
    void maisDe25ParadasLancaIndisponivelSemChamarOGoogle() {
        List<Coordenada> muitas = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            muitas.add(c("-23.5", "-46.6"));
        }

        assertThatThrownBy(() -> provider.otimizarRota(c("-23.5", "-46.6"), muitas, c("-23.5", "-46.6"), Instant.now()))
                .isInstanceOf(MapsIndisponivelException.class);
        servidor.verify();
    }

    @Test
    void erroHttpViraIndisponivelSemVazarAChave() {
        servidor.expect(requestTo(GoogleMapsProvider.URL_ROTAS)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> provider.otimizarRota(c("-23.5", "-46.6"), List.of(c("-23.51", "-46.61")),
                c("-23.53", "-46.63"), Instant.now()))
                .isInstanceOf(MapsIndisponivelException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CHAVE));
    }

    @Test
    void respostaSemRotaViraIndisponivel() {
        servidor.expect(requestTo(GoogleMapsProvider.URL_ROTAS))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.otimizarRota(c("-23.5", "-46.6"), List.of(c("-23.51", "-46.61")),
                c("-23.53", "-46.63"), Instant.now()))
                .isInstanceOf(MapsIndisponivelException.class);
    }

    @Test
    void geocodificaEndereco() {
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/json")))
                .andExpect(queryParam("key", CHAVE))
                .andExpect(queryParam("region", "br"))
                .andExpect(request -> org.junit.jupiter.api.Assertions.assertEquals(
                        "Rua A, 10, Pinheiros, São Paulo - SP, 05422001",
                        org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams()
                                .getFirst("address") == null ? null : java.net.URLDecoder.decode(
                                org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("address"),
                                java.nio.charset.StandardCharsets.UTF_8)))
                .andRespond(withSuccess("""
                        {"status":"OK","results":[{"geometry":{"location":{"lat":-23.5614,"lng":-46.6921}}}]}
                        """, MediaType.APPLICATION_JSON));

        var r = provider.geocodificar("Rua A, 10, Pinheiros, São Paulo - SP, 05422001");

        assertThat(r).isPresent();
        assertThat(r.get().latitude()).isEqualByComparingTo("-23.5614");
        assertThat(r.get().longitude()).isEqualByComparingTo("-46.6921");
    }

    @Test
    void geocodificacaoSemResultadoDevolveVazio() {
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/json")))
                .andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\",\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(provider.geocodificar("endereco que nao existe")).isEmpty();
    }

    @Test
    void falhaNaGeocodificacaoViraIndisponivelSemVazarAChave() {
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/geocode/json")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> provider.geocodificar("qualquer"))
                .isInstanceOf(MapsIndisponivelException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CHAVE));
    }
}
