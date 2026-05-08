package com.dreamhomes.haven.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrictEmailValidatorTest {

    private final StrictEmailValidator validator = new StrictEmailValidator();

    @Test
    void acceptsRealisticAddresses() {
        assertThat(validator.isValid("ada@example.com", null)).isTrue();
        assertThat(validator.isValid("a.b+tag@sub.example.co.uk", null)).isTrue();
        assertThat(validator.isValid("user_name@example.io", null)).isTrue();
    }

    @Test
    void rejectsLaxAddressesThatJakartaEmailWouldAccept() {
        assertThat(validator.isValid("a@b", null)).isFalse();          // no TLD
        assertThat(validator.isValid("a@b.c", null)).isFalse();        // single-letter TLD
        assertThat(validator.isValid("a@.com", null)).isFalse();       // empty domain label
        assertThat(validator.isValid(".a@b.com", null)).isFalse();     // leading dot in local
        assertThat(validator.isValid("a.@b.com", null)).isFalse();     // trailing dot in local
        assertThat(validator.isValid("a@b.com.", null)).isFalse();     // trailing dot
        assertThat(validator.isValid("not-an-email", null)).isFalse();
        assertThat(validator.isValid("two@@signs.com", null)).isFalse();
    }

    @Test
    void delegatesEmptinessToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
    }
}
