package org.tinycircl.pandoc4j.binary.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tinycircl.pandoc4j.binary.PandocBinaryOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PandocBinaryPropertiesTest {

    @Test
    @DisplayName("Spring properties include proxy and debug options")
    void toOptionsIncludesProxyAndDebug() {
        PandocBinaryProperties properties = new PandocBinaryProperties();
        properties.setProxyHost("127.0.0.1");
        properties.setProxyPort(7890);
        properties.setDebug(true);

        PandocBinaryOptions options = properties.toOptions();

        assertEquals("127.0.0.1", options.getProxyHost());
        assertEquals(7890, options.getProxyPort());
        assertTrue(options.isDebug());
    }
}
