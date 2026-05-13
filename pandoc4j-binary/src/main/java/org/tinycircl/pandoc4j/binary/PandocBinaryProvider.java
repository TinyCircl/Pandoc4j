package org.tinycircl.pandoc4j.binary;

import org.tinycircl.pandoc4j.core.PandocInstallation;
import org.tinycircl.pandoc4j.core.PandocInstallationProvider;

import java.util.Optional;

public final class PandocBinaryProvider implements PandocInstallationProvider {

    @Override
    public Optional<PandocInstallation> resolve() {
        if (!PandocBinaryOptions.isProviderEnabled()) {
            return Optional.empty();
        }
        return Optional.of(PandocBinary.getInstallation(PandocBinaryOptions.fromSystemProperties()));
    }
}
