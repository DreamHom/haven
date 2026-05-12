package com.dreamhomes.haven.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotCommonPasswordValidator.class)
public @interface NotCommonPassword {
    String message() default "password is too common — pick something less obvious";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
