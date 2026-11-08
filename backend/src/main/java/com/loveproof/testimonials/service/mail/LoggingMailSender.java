package com.loveproof.testimonials.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.loveproof.testimonials.config.MailNotConfiguredCondition;

/**
 * The mail sender used when no SMTP server is configured.
 *
 * <p>Writing the message to the log keeps the application usable out of the
 * box: a developer registering a local account sees the verification link
 * without standing up a mail server. The token itself is in the body, so this
 * log line is sensitive -- which is why the log level for this class is INFO
 * and the production profile keeps the root logger at WARN.
 *
 * <p>It is registered only when no SMTP host is configured, which is the
 * exact complement of {@link SmtpMailSender}. The two conditions are
 * exclusive, so there is always exactly one {@link MailSender} in the
 * context -- two of them would make the injection point ambiguous and stop
 * the application from starting at all.
 */
@Component
@Conditional(MailNotConfiguredCondition.class)
public class LoggingMailSender implements MailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        LOGGER.info("Email not sent (no SMTP server configured). To: {}, Subject: {}{}{}", to, subject,
                System.lineSeparator(), body);
    }
}
