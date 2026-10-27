package br.com.smartvan.comum.seguranca;

import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.enums.TipoUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quem está chamando a API. O JWT tem subject = usuarioId e claim "tipo" = TipoUsuario.
 * Uso nos controllers/services: {@code UsuarioLogado eu = UsuarioLogado.atual();}
 */
public record UsuarioLogado(Long id, TipoUsuario tipo) {

    public static final String CLAIM_TIPO = "tipo";

    public static UsuarioLogado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new RegraNegocioException(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Faça login para continuar.");
        }
        return new UsuarioLogado(Long.valueOf(jwt.getSubject()), TipoUsuario.valueOf(jwt.getClaimAsString(CLAIM_TIPO)));
    }

    public boolean ehMotorista() { return tipo == TipoUsuario.MOTORISTA; }

    public boolean ehResponsavel() { return tipo == TipoUsuario.RESPONSAVEL; }
}
