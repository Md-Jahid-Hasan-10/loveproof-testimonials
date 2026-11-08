package com.loveproof.testimonials.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when no SMTP host has been configured.
 *
 * <p>The exact complement of {@link MailConfiguredCondition}. The two mail
 * senders are registered on these two conditions, so there is always exactly
 * one of them in the context.
 */
public class MailNotConfiguredCondition implements Condition {

    /**
     * The property that holds the SMTP host.
     */
    private static final String HOST_PROPERTY = "loveproof.mail.smtp.host";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String host = context.getEnvironment().getProperty(HOST_PROPERTY);
        return !StringUtils.hasText(host);
    }
}
