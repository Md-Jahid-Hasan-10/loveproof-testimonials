package com.loveproof.testimonials.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when an SMTP host has actually been configured.
 *
 * <p>Spring's own {@code @ConditionalOnProperty} cannot express "the property
 * is present and not blank", and that is the question that has to be asked
 * here. The host is declared with an empty default in {@code application.yml}
 * so that it is always visible in the configuration, which means the property
 * is present even when no mail server has been set up.
 *
 * <p>Both mail senders are conditional on the same property and their
 * conditions are exclusive: exactly one of them is registered. If both were,
 * the injection point in {@code AuthService} would be ambiguous and the
 * application would refuse to start -- so a missing or blank host has to
 * select the logging sender, not merely fail to select the SMTP one.
 */
public class MailConfiguredCondition implements Condition {

    /**
     * The property that holds the SMTP host.
     */
    private static final String HOST_PROPERTY = "loveproof.mail.smtp.host";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String host = context.getEnvironment().getProperty(HOST_PROPERTY);
        return StringUtils.hasText(host);
    }
}
