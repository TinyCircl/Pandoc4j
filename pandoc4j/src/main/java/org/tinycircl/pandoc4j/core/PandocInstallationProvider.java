package org.tinycircl.pandoc4j.core;

import java.util.Optional;

/**
 * Service Provider Interface for optional Pandoc installation resolvers.
 *
 * <p>The core `pandoc4j` artifact does not download or install Pandoc. Companion
 * artifacts can implement this SPI and register via {@link java.util.ServiceLoader}
 * to provide a managed {@link PandocInstallation}.
 */
public interface PandocInstallationProvider {

    /**
     * Attempts to resolve a Pandoc installation.
     *
     * @return a resolved installation, or {@link Optional#empty()} when this
     * provider is disabled or cannot resolve Pandoc for the current environment
     */
    Optional<PandocInstallation> resolve();
}
