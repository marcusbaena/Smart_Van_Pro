package br.com.smartvan.auth;

import br.com.smartvan.comum.erro.ErroResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/** Spring Security stateless com JWT HS256. 401/403 do filtro saem no JSON de erro do contrato. */
@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

    private final SecretKey chave;

    public SegurancaConfig(@Value("${smartvan.jwt.secret:}") String segredo) {
        if (segredo == null || segredo.length() < 32) {
            throw new IllegalStateException("smartvan.jwt.secret (JWT_SECRET) deve ter pelo menos 32 caracteres.");
        }
        this.chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    @Bean
    public JwtDecoder jwtDecoder(Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        // validade conferida com o Clock do negócio (nos testes dá para avançar o relógio)
        JwtTimestampValidator tempo = new JwtTimestampValidator();
        tempo.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(tempo));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            String tipo = jwt.getClaimAsString("tipo");
            return tipo == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + tipo));
        });
        return conversor;
    }

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http, JwtAuthenticationConverter conversor, JsonMapper json)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/cadastro", "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/escolas").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(conversor))
                        .authenticationEntryPoint((req, res, e) ->
                                escrever(res, json, 401, "NAO_AUTENTICADO", "Faça login para continuar.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                escrever(res, json, 401, "NAO_AUTENTICADO", "Faça login para continuar."))
                        .accessDeniedHandler((req, res, ex) ->
                                escrever(res, json, 403, "SEM_PERMISSAO", "Você não tem permissão para esta ação.")));
        return http.build();
    }

    private static void escrever(HttpServletResponse res, JsonMapper json, int status, String codigo, String msg)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(json.writeValueAsString(ErroResponse.de(status, codigo, msg)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${smartvan.cors.origens:}") String origens) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(origens.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", cfg);
        return fonte;
    }
}
