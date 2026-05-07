package com.dreamhomes.haven.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stricter than jakarta's {@code @Email}, which accepts {@code a@b}. This requires a
 * proper TLD-style domain (at least two letters after the final dot) and a non-empty
 * local part with conventional characters.
 *
 * <p>Always pair with {@code @NotBlank} when the field is required — empty strings pass
 * this constraint by design, so the not-blank check stays separate.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrictEmailValidator.class)
public @interface StrictEmail {
    String message() default "must be a well-formed email address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
