package br.com.smartvan.comum.erro;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exceção padrão de regra de negócio. O {@link TratadorGlobalDeErros} transforma em {@link ErroResponse}.
 * Use os códigos definidos em docs/CONTRATO_API.md.
 */
@Getter
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public RegraNegocioException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public static RegraNegocioException naoEncontrado(String recurso) {
        return new RegraNegocioException(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", recurso + " não encontrado(a).");
    }

    public static RegraNegocioException semPermissao() {
        return new RegraNegocioException(HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Você não tem permissão para esta ação.");
    }

    public static RegraNegocioException conflito(String codigo, String mensagem) {
        return new RegraNegocioException(HttpStatus.CONFLICT, codigo, mensagem);
    }

    public static RegraNegocioException regra(String codigo, String mensagem) {
        return new RegraNegocioException(HttpStatus.UNPROCESSABLE_CONTENT, codigo, mensagem);
    }
}
