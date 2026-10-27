package br.com.smartvan.auth.dto;

import br.com.smartvan.enums.TipoUsuario;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** O cadastro público só cria RESPONSAVEL ou MOTORISTA (ADMINISTRADOR → 400 VALIDACAO). */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TipoCadastroPermitido.Validador.class)
public @interface TipoCadastroPermitido {

    String message() default "deve ser RESPONSAVEL ou MOTORISTA";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<TipoCadastroPermitido, TipoUsuario> {
        @Override
        public boolean isValid(TipoUsuario tipo, ConstraintValidatorContext context) {
            return tipo == null || tipo == TipoUsuario.RESPONSAVEL || tipo == TipoUsuario.MOTORISTA;
        }
    }
}
