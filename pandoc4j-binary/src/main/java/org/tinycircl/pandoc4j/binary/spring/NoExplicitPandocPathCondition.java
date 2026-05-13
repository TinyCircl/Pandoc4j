package org.tinycircl.pandoc4j.binary.spring;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.tinycircl.pandoc4j.core.PandocInstallation;

final class NoExplicitPandocPathCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isBlank(context.getEnvironment().getProperty("pandoc.executable-path"))
                && isBlank(System.getProperty(PandocInstallation.SYSTEM_PROPERTY))
                && isBlank(System.getenv(PandocInstallation.ENV_VARIABLE));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
