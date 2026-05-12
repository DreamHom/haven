package com.dreamhomes.haven.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrictEmailValidator implements ConstraintValidator<StrictEmail, String> {

    private static final Pattern PATTERN = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9._%+\\-]*[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9.\\-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        return PATTERN.matcher(value).matches();
    }
}
