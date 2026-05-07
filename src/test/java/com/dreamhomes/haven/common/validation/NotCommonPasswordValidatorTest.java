package com.dreamhomes.haven.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotCommonPasswordValidatorTest {

    private final NotCommonPasswordValidator validator = new NotCommonPasswordValidator();

    @Test
    void rejectsCommonPasswords() {
        assertThat(validator.isValid("password", null)).isFalse();
        assertThat(validator.isValid("12345678", null)).isFalse();
        assertThat(validator.isValid("qwerty123", null)).isFalse();
        assertThat(validator.isValid("Password", null)).isFalse(); // case-insensitive
    }

    @Test
    void acceptsRealisticPasswords() {
        assertThat(validator.isValid("a-real-passphrase-7x9", null)).isTrue();
        assertThat(validator.isValid("Tr0ub4dor&3", null)).isTrue();
    }

    @Test
    void allowsNullSoNotBlankCanOwnPresence() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
