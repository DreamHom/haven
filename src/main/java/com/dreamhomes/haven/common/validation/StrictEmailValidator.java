package com.dreamhomes.haven.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrictEmailValidator implements ConstraintValidator<StrictEmail, String> {

    /**
     * Pragmatic email regex: one or more local-part chars, exactly one '@', a domain
     * with at least one dot, and a TLD of two or more letters. Rejects {@code a@b},
     * {@code a@b.}, leading/trailing dots, and most obviously-malformed inputs.
     * Not RFC 5322 compliant (no email regex really is — accept that, screen the
     * obvious junk, and rely on a confirmation email later for true verification).
     */
    private static final Pattern PATTERN = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9._%+\\-]*[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9.\\-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isEmpty()) {
            // Pair with @NotBlank to enforce presence; absence is not the email validator's concern.
            return true;
        }
        return PATTERN.matcher(value).matches();
    }
}
