package com.loveproof.testimonials.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.loveproof.testimonials.config.MailConfiguredCondition;

/**
 * The mail sender used when an SMTP server is configured.
 *
 * <p>Only registered when {@code loveproof.mail.smtp.host} has a non-blank
 * value, so the application falls back to {@link LoggingMailSender}
 * otherwise. The two conditions are exclusive, so there is always exactly
 * one mail sender in the context.
 */
@Component
@Conditional(MailConfiguredCondition.class)
public class SmtpMailSender implements MailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender mailSender;

    private final String fromAddress;

    /**
     * Creates the sender.
     *
     * @param mailSender   the underlying sender
     * @param fromAddress  the address the messages come from
     */
    public SmtpMailSender(JavaMailSender mailSender,
            @org.springframework.beans.factory.annotation.Value("${loveproof.mail.from:no-reply@localhost}")
                    String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (RuntimeException failure) {
            // A failed email must not fail the request that triggered it: the
            // account exists and the user can ask for another link.
            LOGGER.warn("Could not send the email to {}. The account is unaffected.", to, failure);
        }
    }
}
