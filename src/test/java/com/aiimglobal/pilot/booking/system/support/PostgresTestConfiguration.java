package com.aiimglobal.pilot.booking.system.support;

import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.aiimglobal.pilot.booking.system.user.persistence.RoleRepository;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18-alpine")
                .withTmpFs(Map.of("/var/lib/postgresql", "rw"))
                .withDatabaseName("pilot_booking_system")
                .withUsername("pilot_booking_test")
                .withPassword("pilot_booking_test");
    }

    @Bean
    DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate) {
        return new DatabaseCleaner(jdbcTemplate);
    }

    @Bean
    TestDataFactory testDataFactory(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        return new TestDataFactory(userRepository, roleRepository, passwordEncoder);
    }
}
