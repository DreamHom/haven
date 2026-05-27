package com.dreamhomes.haven.verification.automation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the {@code haven.verification.provider} config switches which
 * {@link VerificationProvider} bean is active — even the scaffolded providers (whose
 * method bodies throw {@link UnsupportedOperationException}) are picked up by the
 * conditional, so v2 only has to fill the bodies in, not change any DI wiring.
 */
class VerificationProviderSwapTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(ProvidersConfig.class);

    @Test
    void defaultsToMockProviderWhenPropertyIsAbsent() {
        contextRunner.run(ctx -> {
            VerificationProvider active = ctx.getBean(VerificationProvider.class);
            assertThat(active).isInstanceOf(MockVerificationProvider.class);
            assertThat(active.name()).isEqualTo("MOCK");
        });
    }

    @Test
    void picksSmileIdProviderWhenPropertyIsSmileId() {
        contextRunner
                .withPropertyValues("haven.verification.provider=smile-id")
                .run(ctx -> {
                    VerificationProvider active = ctx.getBean(VerificationProvider.class);
                    assertThat(active).isInstanceOf(SmileIdVerificationProvider.class);
                    assertThatThrownBy(() -> active.verifyOwnerIdentity(
                            new OwnerIdentityCheckRequest(1L, 2L, "{}")))
                            .isInstanceOf(UnsupportedOperationException.class)
                            .hasMessageContaining("Smile ID");
                });
    }

    @Test
    void picksDojahProviderWhenPropertyIsDojah() {
        contextRunner
                .withPropertyValues("haven.verification.provider=dojah")
                .run(ctx -> {
                    VerificationProvider active = ctx.getBean(VerificationProvider.class);
                    assertThat(active).isInstanceOf(DojahVerificationProvider.class);
                    assertThatThrownBy(() -> active.verifyApplicantIdentity(
                            new ApplicantIdentityCheckRequest(1L, 2L, "{}")))
                            .isInstanceOf(UnsupportedOperationException.class)
                            .hasMessageContaining("Dojah");
                });
    }

    @Configuration
    @Import({MockVerificationProvider.class,
            SmileIdVerificationProvider.class,
            DojahVerificationProvider.class})
    static class ProvidersConfig {
    }
}
