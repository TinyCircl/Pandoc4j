package org.tinycircl.pandoc4j.binary.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.tinycircl.pandoc4j.Pandoc4j;
import org.tinycircl.pandoc4j.binary.PandocBinary;
import org.tinycircl.pandoc4j.core.PandocInstallation;
import org.tinycircl.pandoc4j.spring.PandocAutoConfiguration;

@AutoConfiguration(before = PandocAutoConfiguration.class)
@ConditionalOnClass(Pandoc4j.class)
@ConditionalOnProperty(prefix = "pandoc.binary", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PandocBinaryProperties.class)
public class PandocBinaryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NoExplicitPandocPathCondition.class)
    public PandocInstallation pandocInstallation(PandocBinaryProperties properties) {
        return PandocBinary.getInstallation(properties.toOptions());
    }
}
