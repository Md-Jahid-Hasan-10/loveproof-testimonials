package com.loveproof.testimonials.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Verifies that exactly one {@link MailSender} is registered, in either configuration.
 *
 * <p>Both senders are conditional on the same property, and their conditions
 * have to be exclusive. If both were registered, the injection point in
 * {@code AuthService} would be ambiguous and the application would refuse to
 * start — a failure that only shows up when somebody configures a mail
 * server, which is exactly the moment it is most inconvenient.
 *
 * <p>The subtle part is the empty string. The SMTP host is declared with an
 * empty default in {@code application.yml}, so the property is always
 * present; a condition that merely asks "is the property there?" would
 * register the SMTP sender even when no host was configured, leaving both
 * senders in the context. These tests cover the blank value, not only the
 * absent one.
 */
class MailSenderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailSenderSelectionTest.TestApplication.class);

    @Test
    void theLoggingSenderIsUsedWhenNoHostIsConfigured() {
        runner.withPropertyValues("loveproof.mail.smtp.host=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(MailSender.class))
                            .as("Only the logging sender may be registered")
                            .hasSize(1);
                    assertThat(context.getBean(MailSender.class))
                            .isInstanceOf(LoggingMailSender.class);
                });
    }

    @Test
    void theLoggingSenderIsUsedWhenTheHostIsAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(MailSender.class)).hasSize(1);
            assertThat(context.getBean(MailSender.class))
                    .isInstanceOf(LoggingMailSender.class);
        });
    }

    @Test
    void theLoggingSenderIsUsedWhenTheHostIsOnlyWhitespace() {
        runner.withPropertyValues("loveproof.mail.smtp.host=   ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(MailSender.class)).hasSize(1);
                    assertThat(context.getBean(MailSender.class))
                            .isInstanceOf(LoggingMailSender.class);
                });
    }

    /**
     * The SMTP sender needs a real {@code JavaMailSender}, which needs a
     * host and a port. They are supplied here as stubs: this test is about
     * which sender is <em>selected</em>, not about sending anything.
     */
    @Test
    void theSmtpSenderIsUsedWhenAHostIsConfigured() {
        runner.withPropertyValues(
                        "loveproof.mail.smtp.host=smtp.example.com",
                        "spring.mail.host=smtp.example.com",
                        "spring.mail.port=2525")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(MailSender.class))
                            .as("Only the SMTP sender may be registered")
                            .hasSize(1);
                    assertThat(context.getBean(MailSender.class))
                            .isInstanceOf(SmtpMailSender.class);
                });
    }

    /**
     * A minimal context that imports the two senders.
     *
     * <p>It deliberately does not start the whole application: the point of
     * these tests is the selection, and a full context would need a database.
     */
    @Configuration
    @Import({SmtpMailSender.class, LoggingMailSender.class})
    static class TestApplication {

        /**
         * A stand-in for Spring's mail auto-configuration, so the SMTP
         * sender can be created without a real mail server.
         *
         * @return a JavaMailSender
         */
        @Bean
        org.springframework.mail.javamail.JavaMailSender javaMailSender() {
            return new org.springframework.mail.javamail.JavaMailSenderImpl();
        }
    }
}
