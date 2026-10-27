package br.com.smartvan.auth;

import br.com.smartvan.comum.seguranca.UsuarioLogado;
import br.com.smartvan.enums.TipoUsuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Emite o JWT HS256: sub = id do usuário, claim "tipo" = TipoUsuario. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration expiracao;

    public JwtService(JwtEncoder encoder, Clock clock, @Value("${smartvan.jwt.expiracao-horas:8}") long horas) {
        this.encoder = encoder;
        this.clock = clock;
        this.expiracao = Duration.ofHours(horas);
    }

    public record TokenEmitido(String token, Instant expiraEm) {
    }

    public TokenEmitido emitir(Long usuarioId, TipoUsuario tipo) {
        Instant agora = clock.instant();
        Instant expira = agora.plus(expiracao);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(usuarioId))
                .issuedAt(agora)
                .expiresAt(expira)
                .claim(UsuarioLogado.CLAIM_TIPO, tipo.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenEmitido(token, expira);
    }
}
