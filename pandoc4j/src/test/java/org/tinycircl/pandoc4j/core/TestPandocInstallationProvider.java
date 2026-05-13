package org.tinycircl.pandoc4j.core;

import java.nio.file.Path;
import java.util.Optional;

public final class TestPandocInstallationProvider implements PandocInstallationProvider {

    static final String PROPERTY = "pandoc4j.test.provider.path";

    @Override
    public Optional<PandocInstallation> resolve() {
        String path = System.getProperty(PROPERTY);
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PandocInstallation.at(Path.of(path)));
    }
}
