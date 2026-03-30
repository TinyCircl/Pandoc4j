package org.tinycircl.pandoc4j.spring;

import org.tinycircl.pandoc4j.ConversionRequest;
import org.tinycircl.pandoc4j.Format;
import org.tinycircl.pandoc4j.core.PandocInstallation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PandocAutoConfiguration} using {@link ApplicationContextRunner}.
 *
 * <p>Uses AssertJ + Spring Boot Test's lightweight context runner –
 * no full Spring Boot application needed.
 */
@Tag("integration")
class PandocAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PandocAutoConfiguration.class));

    // ── Bean registration ─────────────────────────────────────────────────

    @Test
    @DisplayName("Auto-configuration registers PandocInstallation and PandocClient beans")
    void defaultBeansAreRegistered() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PandocInstallation.class);
            assertThat(context).hasSingleBean(PandocClient.class);
        });
    }

    @Test
    @DisplayName("PandocClient bean is functional – convertText works")
    void pandocClientConverts() {
        contextRunner.run(context -> {
            PandocClient client = context.getBean(PandocClient.class);
            String html = client.convertText("# Hello", Format.MARKDOWN, Format.HTML5);
            assertThat(html).contains("<h1");
        });
    }

    @Test
    @DisplayName("PandocClient.getVersion() returns a non-blank version")
    void pandocClientVersion() {
        contextRunner.run(context -> {
            PandocClient client = context.getBean(PandocClient.class);
            assertThat(client.getVersion()).isNotBlank();
        });
    }

    // ── Properties ────────────────────────────────────────────────────────

    @Test
    @DisplayName("pandoc.timeout-seconds property is propagated to ConversionRequest builder")
    void timeoutPropertyIsApplied() {
        contextRunner
                .withPropertyValues("pandoc.timeout-seconds=30")
                .run(context -> {
                    PandocClient client = context.getBean(PandocClient.class);
                    ConversionRequest request = client.builder()
                            .from(Format.MARKDOWN)
                            .to(Format.HTML5)
                            .build();
                    assertThat(request)
                            .extracting("timeoutSeconds")
                            .isEqualTo(30L);
                });
    }

    @Test
    @DisplayName("pandoc.executable-path with invalid path causes context failure")
    void invalidExecutablePathFailsContext() {
        contextRunner
                .withPropertyValues("pandoc.executable-path=/nonexistent/pandoc")
                .run(context ->
                        assertThat(context).hasFailed()
                );
    }

    // ── Back-off: user-defined beans ──────────────────────────────────────

    @Test
    @DisplayName("User-defined PandocInstallation bean suppresses auto-configured one")
    void userDefinedInstallationTakesPrecedence() {
        contextRunner
                .withBean(PandocInstallation.class, PandocInstallation::detect)
                .run(context -> {
                    assertThat(context).hasSingleBean(PandocInstallation.class);
                    assertThat(context).hasSingleBean(PandocClient.class);
                });
    }

    @Test
    @DisplayName("User-defined PandocClient bean suppresses auto-configured one")
    void userDefinedClientTakesPrecedence() {
        PandocInstallation inst = PandocInstallation.detect();
        contextRunner
                .withBean(PandocClient.class, () -> new PandocClient(inst, 60))
                .run(context ->
                        assertThat(context).hasSingleBean(PandocClient.class)
                );
    }

    // ── listFormats ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PandocClient.listInputFormats() returns non-empty list via Spring bean")
    void listInputFormatsViaClient() {
        contextRunner.run(context -> {
            PandocClient client = context.getBean(PandocClient.class);
            assertThat(client.listInputFormats())
                    .isNotEmpty()
                    .contains("markdown");
        });
    }

    @Test
    @DisplayName("PandocClient.listOutputFormats() returns non-empty list via Spring bean")
    void listOutputFormatsViaClient() {
        contextRunner.run(context -> {
            PandocClient client = context.getBean(PandocClient.class);
            assertThat(client.listOutputFormats())
                    .isNotEmpty()
                    .contains("html5");
        });
    }
}
