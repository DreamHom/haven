package com.dreamhomes.haven.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects passwords that appear in a small embedded deny-list of common/weak choices
 * ("password", "12345678", "qwertyui", etc.). Comparison is case-insensitive.
 *
 * <p>Length and character composition are still enforced separately by {@code @Size}.
 * This is the cheap second layer that catches passwords which technically meet the
 * length rule but are obviously guessable.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotCommonPasswordValidator.class)
public @interface NotCommonPassword {
    String message() default "password is too common — pick something less obvious";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
