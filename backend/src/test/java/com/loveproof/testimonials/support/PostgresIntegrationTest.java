package com.loveproof.testimonials.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that need a real PostgreSQL 16 instance.
 *
 * <p>A real database is used deliberately: H2's PostgreSQL compatibility mode would not exercise the
 * CHECK constraints, the partial indexes or the Flyway migrations faithfully (NFR-TEST-1).
 *
 * <p>The container is a JVM-wide singleton rather than a per-class instance. Declaring it as a
 * {@code @Container} field in a base class would start and stop a fresh container for every test
 * class, which is slow and races the Spring context's connection pool against the shutdown.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresIntegrationTest {

    /**
     * The shared PostgreSQL 16 container.
     *
     * <p>Started lazily on first use and left running for the rest of the JVM's life.
     */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("loveproof_test")
            .withUsername("loveproof")
            .withPassword("loveproof");

    static {
        POSTGRES.start();
    }

    /**
     * Points the datasource at the container.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
