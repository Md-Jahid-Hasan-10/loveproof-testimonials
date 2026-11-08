package com.loveproof.testimonials.service.mail;

/**
 * Sends an email.
 *
 * <p>An interface rather than a direct dependency on a mail library, so the
 * application can run without any mail server configured -- the messages are
 * written to the log instead -- and so a test can assert on what would have
 * been sent.
 */
public interface MailSender {

    /**
     * Sends a plain-text email.
     *
     * @param to      the recipient
     * @param subject the subject line
     * @param body    the message body
     */
    void send(String to, String subject, String body);
}
