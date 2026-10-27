package br.com.smartvan.rota.maps;

import br.com.smartvan.comum.maps.Coordenada;
import br.com.smartvan.comum.maps.MapsIndisponivelException;
import br.com.smartvan.comum.maps.MapsProvider;
import br.com.smartvan.comum.maps.RotaOtimizada;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Google Routes API (otimização de paradas) e Geocoding API. A chave vem só da configuração e nunca é logada.
 * Uma chamada por rota gerada e uma por endereço sem coordenada (custo: ver seção 10 do contexto original).
 */
public class GoogleMapsProvider implements MapsProvider {

    public static final String URL_ROTAS = "https://routes.googleapis.com/directions/v2:computeRoutes";
    public static final String URL_GEOCODING = "https://maps.googleapis.com/maps/api/geocode/json";
    static final String FIELD_MASK = "routes.optimizedIntermediateWaypointIndex,routes.legs.duration,"
            + "routes.legs.distanceMeters,routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline";
    static final int MAX_PARADAS = 25;

    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String chave;
    private final String urlRotas;
    private final String urlGeocoding;

    public GoogleMapsProvider(String chave) {
        this(clienteComTimeout(), chave, URL_ROTAS, URL_GEOCODING);
    }

    public GoogleMapsProvider(RestClient http, String chave, String urlRotas, String urlGeocoding) {
        this.http = http;
        this.chave = chave;
        this.urlRotas = urlRotas;
        this.urlGeocoding = urlGeocoding;
    }

    /** Timeouts curtos (5 s) para o Google nunca travar a geracao da rota. */
    static RestClient clienteComTimeout() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public Optional<Coordenada> geocodificar(String enderecoCompleto) {
        try {
            String corpo = http.get()
                    .uri(urlGeocoding + "?address={a}&key={k}&region=br", enderecoCompleto, chave)
                    .retrieve().body(String.class);
            JsonNode raiz = json.readTree(corpo);
            if (!"OK".equals(raiz.path("status").asString(""))) {
                return Optional.empty();
            }
            JsonNode loc = raiz.path("results").path(0).path("geometry").path("location");
            if (!loc.has("lat") || !loc.has("lng")) {
                return Optional.empty();
            }
            return Optional.of(new Coordenada(new BigDecimal(loc.get("lat").asString()),
                    new BigDecimal(loc.get("lng").asString())));
        } catch (Exception e) {
            // mensagens de erro HTTP podem conter a URL com a chave: não propaga a causa nem a mensagem
            throw new MapsIndisponivelException("Falha ao geocodificar no Google (" + e.getClass().getSimpleName() + ").");
        }
    }

    @Override
    public RotaOtimizada otimizarRota(Coordenada partida, List<Coordenada> paradas, Coordenada destino, Instant saida) {
        if (paradas.size() > MAX_PARADAS) {
            throw new MapsIndisponivelException("Mais de " + MAX_PARADAS + " paradas: otimização do Google não usada.");
        }
        Instant agora = Instant.now();
        Instant partidaEm = saida.isAfter(agora) ? saida : agora.plusSeconds(60);
        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("origin", ponto(partida));
            corpo.put("destination", ponto(destino));
            if (!paradas.isEmpty()) {
                corpo.put("intermediates", paradas.stream().map(GoogleMapsProvider::ponto).toList());
                corpo.put("optimizeWaypointOrder", true);
            }
            corpo.put("travelMode", "DRIVE");
            corpo.put("routingPreference", "TRAFFIC_AWARE");
            corpo.put("departureTime", partidaEm.toString());

            String resposta = http.post().uri(urlRotas)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", chave)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(json.writeValueAsString(corpo))
                    .retrieve().body(String.class);
            return interpretar(json.readTree(resposta), paradas.size());
        } catch (MapsIndisponivelException e) {
            throw e;
        } catch (Exception e) {
            throw new MapsIndisponivelException("Falha ao otimizar rota no Google (" + e.getClass().getSimpleName() + ").");
        }
    }

    RotaOtimizada interpretar(JsonNode raiz, int totalParadas) {
        JsonNode rota = raiz.path("routes").path(0);
        if (rota.isMissingNode()) {
            throw new MapsIndisponivelException("Google não devolveu rota.");
        }
        List<Integer> ordem = new ArrayList<>();
        JsonNode idx = rota.path("optimizedIntermediateWaypointIndex");
        if (idx.isArray()) {
            idx.forEach(n -> ordem.add(n.asInt()));
        } else {
            for (int i = 0; i < totalParadas; i++) {
                ordem.add(i);
            }
        }
        if (ordem.size() != totalParadas) {
            throw new MapsIndisponivelException("Resposta do Google com ordem inesperada.");
        }
        // n paradas intermediárias => n+1 pernas; a perna i chega na i-ésima parada (já na ordem ótima)
        JsonNode pernas = rota.path("legs");
        List<Integer> acumulado = new ArrayList<>();
        int soma = 0;
        for (int i = 0; i < totalParadas; i++) {
            soma += segundos(pernas.path(i).path("duration"));
            acumulado.add(soma);
        }
        int total = segundos(rota.path("duration"));
        if (total == 0) {
            total = soma + segundos(pernas.path(totalParadas).path("duration"));
        }
        int metros = rota.path("distanceMeters").asInt(0);
        JsonNode poly = rota.path("polyline").path("encodedPolyline");
        return new RotaOtimizada(ordem, acumulado, total, metros, poly.isMissingNode() ? null : poly.asString());
    }

    /** Duração do Google vem como texto "123s". */
    private static int segundos(JsonNode duracao) {
        String s = duracao.isMissingNode() || duracao.isNull() ? "" : duracao.asString("");
        if (s.endsWith("s")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Object ponto(Coordenada c) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", c.latitude().doubleValue(), "longitude", c.longitude().doubleValue())));
    }
}
