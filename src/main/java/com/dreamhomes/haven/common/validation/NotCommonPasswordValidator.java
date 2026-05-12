package com.dreamhomes.haven.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;
import java.util.Set;

public class NotCommonPasswordValidator implements ConstraintValidator<NotCommonPassword, String> {
    
    private static final Set<String> COMMON = Set.of(
            "00000000",
            "11111111",
            "12345678",
            "123456789",
            "1234567890",
            "abcdefgh",
            "abc12345",
            "admin123",
            "asdfghjkl",
            "iloveyou",
            "letmein123",
            "password",
            "password1",
            "password123",
            "qwerty123",
            "qwertyui",
            "qwertyuiop",
            "welcome1",
            "welcome123"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return true;
        }
        return !COMMON.contains(value.toLowerCase(Locale.ROOT));
    }
}
