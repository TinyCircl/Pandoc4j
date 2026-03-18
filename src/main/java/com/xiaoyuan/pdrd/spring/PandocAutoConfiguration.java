package com.xiaoyuan.pdrd.spring;

import com.xiaoyuan.pdrd.Pandoc4j;
import com.xiaoyuan.pdrd.core.PandocInstallation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Spring Boot auto-configuration for pandoc4j.
 *
 * <p>Activated automatically when {@link Pandoc4j} is on the classpath and
 * Spring Boot's auto-configuration mechanism is active.
 *
 * <p>Registers two beans:
 * <ul>
 *   <li>{@link PandocInstallation} – the resolved Pandoc binary, configured once at
 *       startup. Override by declaring your own {@code PandocInstallation} bean.</li>
 *   <li>{@link PandocClient} – the injectable conversion service. Override by
 *       declaring your own {@code PandocClient} bean.</li>
 * </ul>
 *
 * <p>Configurable via {@code application.yml / application.properties}:
 * <pre>{@code
 * pandoc:
 *   executable-path: /usr/local/bin/pandoc   # optional
 *   timeout-seconds: 60                       # optional, default 120
 * }</pre>
 */
@AutoConfiguration
@ConditionalOnClass(Pandoc4j.class)
@EnableConfigurationProperties(PandocProperties.class)
public class PandocAutoConfiguration {

    /**
     * Creates a {@link PandocInstallation} bean from the configured properties.
     *
     * <ul>
     *   <li>If {@code pandoc.executable-path} is set, that path is used directly.</li>
     *   <li>Otherwise Pandoc is auto-detected via {@link PandocInstallation#detect()}.</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean
    public PandocInstallation pandocInstallation(PandocProperties properties) {
        String customPath = properties.getExecutablePath();
        if (customPath != null && !customPath.isBlank()) {
            return PandocInstallation.at(Path.of(customPath));
        }
        return PandocInstallation.detect();
    }

    /**
     * Creates the {@link PandocClient} bean, wired with the resolved
     * {@link PandocInstallation} and the configured timeout.
     */
    @Bean
    @ConditionalOnMissingBean
    public PandocClient pandocClient(PandocInstallation installation,
                                     PandocProperties properties) {
        return new PandocClient(installation, properties.getTimeoutSeconds());
    }
}
