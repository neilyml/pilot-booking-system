package com.aiimglobal.pilot.booking.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    @Autowired
    protected Flyway flyway;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PostgreSQLContainer postgresContainer;

    protected RestTestClient restTestClient;

    @BeforeEach
    void prepareIntegrationTest() {
        databaseCleaner.clean();
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> json(byte[] responseBody) throws Exception {
        assertThat(responseBody).isNotNull();
        return objectMapper.readValue(new String(responseBody, StandardCharsets.UTF_8), Map.class);
    }
}
