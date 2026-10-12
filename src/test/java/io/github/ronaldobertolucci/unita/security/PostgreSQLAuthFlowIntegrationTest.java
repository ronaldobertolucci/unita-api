package io.github.ronaldobertolucci.unita.security;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;

/** Run only against a dedicated disposable PostgreSQL database: these tests clear refresh sessions. */
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_POSTGRES_URL", matches = ".+")
@TestPropertySource(properties = {
        "spring.datasource.url=${AUTH_TEST_POSTGRES_URL}",
        "spring.datasource.username=auth_test",
        "spring.datasource.password=auth_test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class PostgreSQLAuthFlowIntegrationTest extends AuthFlowIntegrationTest {}
