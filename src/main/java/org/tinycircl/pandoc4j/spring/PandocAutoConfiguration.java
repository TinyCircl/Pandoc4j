package org.tinycircl.pandoc4j.spring;

import org.tinycircl.pandoc4j.Pandoc4j;
import org.tinycircl.pandoc4j.core.PandocInstallation;
import org.tinycircl.pandoc4j.exception.PandocNotFoundException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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

    private static final java.util.logging.Logger log =
            java.util.logging.Logger.getLogger(PandocAutoConfiguration.class.getName());

    /**
     * Creates a {@link PandocInstallation} bean from the configured properties.
     *
     * <ul>
     *   <li>If {@code pandoc.executable-path} is set, that path is used directly
     *       (throws {@link PandocNotFoundException} if the path does not exist).</li>
     *   <li>Otherwise Pandoc is auto-detected. If not found, logs a WARN and returns
     *       {@code null} so the application can start without Pandoc installed.
     *       The {@link PandocClient} bean will also be skipped in that case.</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean
    public PandocInstallation pandocInstallation(PandocProperties properties) {
        String customPath = properties.getExecutablePath();
        if (customPath != null && !customPath.isBlank()) {
            return PandocInstallation.at(Path.of(customPath));
        }
        try {
            return PandocInstallation.detect();
        } catch (PandocNotFoundException e) {
            log.warning("[pandoc4j] Pandoc executable not found on PATH – pandoc4j features (docx->markdown, etc.) "
                    + "will be disabled. Install Pandoc (https://pandoc.org/installing.html) "
                    + "or set pandoc.executable-path to enable.");
            return null;
        }
    }

    /**
     * Creates the {@link PandocClient} bean, wired with the resolved
     * {@link PandocInstallation} and the configured timeout.
     * Only registered when a {@link PandocInstallation} bean is present.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PandocInstallation.class)
    public PandocClient pandocClient(PandocInstallation installation,
                                     PandocProperties properties) {
        return new PandocClient(installation, properties.getTimeoutSeconds());
    }
}
