package org.tinycircl.pandoc4j.binary.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tinycircl.pandoc4j.spring.PandocAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PandocBinaryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PandocBinaryAutoConfiguration.class,
                    PandocAutoConfiguration.class));

    @Test
    @DisplayName("pandoc.binary.enabled=false keeps Spring startup on local-only detection")
    void disabledBinaryDoesNotFailStartup() {
        contextRunner
                .withPropertyValues("pandoc.binary.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("Explicit pandoc.executable-path is still owned by core auto-configuration")
    void explicitExecutablePathStillWins() {
        contextRunner
                .withPropertyValues("pandoc.executable-path=/nonexistent/pandoc")
                .run(context -> assertThat(context).hasFailed());
    }
}
