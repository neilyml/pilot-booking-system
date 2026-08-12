package com.aiimglobal.pilot.booking.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntegrationTestFoundationIT extends IntegrationTestBase {

    @Test
    void startsApplicationWithPostgresFlywayAndLiveHttp() {
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(postgresContainer.getDatabaseName()).isEqualTo("pilot_booking_system");
        assertThat(jdbcTemplate.queryForObject("show server_version_num", Integer.class))
                .isBetween(180_000, 189_999);
        assertThat(jdbcTemplate.queryForObject("show data_directory", String.class))
                .isEqualTo("/var/lib/postgresql/18/docker");
        assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(flyway.info().applied()).anySatisfy(migration ->
                assertThat(migration.getVersion().toString()).isEqualTo("0.0.1"));

        restTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void cleansApplicationTablesWithoutTouchingFlywayHistory() {
        jdbcTemplate.update("insert into integration_test_records (description) values (?)", "clean me");
        assertThat(rowCount("integration_test_records")).isEqualTo(1);

        databaseCleaner.clean();

        assertThat(rowCount("integration_test_records")).isZero();
        assertThat(rowCount("flyway_schema_history")).isPositive();
    }

    private int rowCount(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }
}
