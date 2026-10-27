package br.com.smartvan.suporte;

import br.com.smartvan.auth.JwtService;
import br.com.smartvan.enums.TipoUsuario;
import br.com.smartvan.model.Usuario;
import org.springframework.stereotype.Component;

/** Gera JWT válidos para MockMvc: {@code .header("Authorization", tokens.bearer(usuario))}. */
@Component
public class Tokens {

    private final JwtService jwt;

    public Tokens(JwtService jwt) {
        this.jwt = jwt;
    }

    /** Só o token (sem o prefixo "Bearer "). */
    public String para(Usuario usuario) {
        return para(usuario.getId(), usuario.getTipo());
    }

    public String para(Long usuarioId, TipoUsuario tipo) {
        return jwt.emitir(usuarioId, tipo).token();
    }

    /** Valor pronto para o header Authorization. */
    public String bearer(Usuario usuario) {
        return "Bearer " + para(usuario);
    }

    public String bearer(Long usuarioId, TipoUsuario tipo) {
        return "Bearer " + para(usuarioId, tipo);
    }
}
