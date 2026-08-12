package com.aiimglobal.pilot.booking.system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class ApiHardeningIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password-not-for-logs";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void boundsPagesAndUsesStableNewestFirstOrderingAndStatusFiltering() throws Exception {
        Principal owner = registerOwner("paged-owner@example.com");
        Instant firstCreatedAt = Instant.now().minusSeconds(100);
        for (int index = 0; index < 22; index++) {
            insertVessel(
                    owner.id(),
                    index % 2 == 0 ? "PENDING" : "APPROVED",
                    "PAGE-" + index,
                    firstCreatedAt.plusSeconds(index));
        }

        restTestClient.get()
                .uri("/api/v1/vessels")
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(20)
                .jsonPath("$.content[0].registrationNumber").isEqualTo("REG-PAGE-21")
                .jsonPath("$.content[19].registrationNumber").isEqualTo("REG-PAGE-2")
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(22)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.first").isEqualTo(true)
                .jsonPath("$.last").isEqualTo(false);

        restTestClient.get()
                .uri("/api/v1/vessels?page=1&size=3&status=APPROVED")
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(3)
                .jsonPath("$.content[0].status").isEqualTo("APPROVED")
                .jsonPath("$.content[1].status").isEqualTo("APPROVED")
                .jsonPath("$.content[2].status").isEqualTo("APPROVED")
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(3)
                .jsonPath("$.totalElements").isEqualTo(11)
                .jsonPath("$.totalPages").isEqualTo(4);
    }

    @Test
    void returnsPaginationMetadataFromEveryCollectionEndpoint() throws Exception {
        Principal owner = registerOwner("collection-owner@example.com");
        Principal admin = createAdmin();

        List<Request> requests = List.of(
                new Request("/api/v1/vessels?size=1", owner.token()),
                new Request("/api/v1/routes?size=1", owner.token()),
                new Request("/api/v1/coupons?size=1", owner.token()),
                new Request("/api/v1/bookings?size=1", owner.token()),
                new Request("/api/v1/admin/vessels?size=1", admin.token()),
                new Request("/api/v1/admin/bookings?size=1", admin.token()),
                new Request("/api/v1/admin/pilots?size=1", admin.token()),
                new Request("/api/v1/admin/pilots/available?size=1&serviceDate="
                        + LocalDate.now().plusDays(1), admin.token()));

        for (Request request : requests) {
            restTestClient.get()
                    .uri(request.uri())
                    .headers(headers -> headers.setBearerAuth(request.token()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.page").isEqualTo(0)
                    .jsonPath("$.size").isEqualTo(1)
                    .jsonPath("$.totalElements").isNumber()
                    .jsonPath("$.totalPages").isNumber()
                    .jsonPath("$.first").isBoolean()
                    .jsonPath("$.last").isBoolean();
        }
    }

    @Test
    void returnsStableApiErrorsForInvalidPaginationAndFilters() throws Exception {
        Principal owner = registerOwner("invalid-page-owner@example.com");
        Principal admin = createAdmin();

        List<Request> requests = List.of(
                new Request("/api/v1/vessels?page=-1", owner.token()),
                new Request("/api/v1/bookings?size=0", owner.token()),
                new Request("/api/v1/coupons?size=101", owner.token()),
                new Request("/api/v1/vessels?status=UNKNOWN", owner.token()),
                new Request("/api/v1/admin/bookings?status=UNKNOWN", admin.token()),
                new Request("/api/v1/admin/pilots/available?serviceDate=not-a-date", admin.token()));

        for (Request request : requests) {
            assertInvalidParameter(request);
        }
    }

    @Test
    void recordsSafeActorResourceAndActionContextWithoutSecrets(CapturedOutput output)
            throws Exception {
        Principal owner = registerOwner("logged-owner@example.com");
        byte[] tokenBytes = owner.token().getBytes(StandardCharsets.UTF_8);

        restTestClient.post()
                .uri("/api/v1/vessels")
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "name", "MV Logged",
                        "registrationNumber", "REG-LOGGED",
                        "vesselType", "Container Ship"))
                .exchange()
                .expectStatus().isCreated();

        assertThat(output)
                .contains("action=owner_registered actor=logged-owner@example.com userId=")
                .contains("action=login_succeeded actor=logged-owner@example.com")
                .contains("action=vessel_registered actor=logged-owner@example.com vesselId=")
                .doesNotContain(OWNER_PASSWORD)
                .doesNotContain(new String(tokenBytes, StandardCharsets.UTF_8));
    }

    @Test
    void installsIndexesForCommonOwnerAndStatusPages() {
        List<String> indexNames = jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                """, String.class);

        assertThat(indexNames).contains(
                "idx_vessels_owner_status_created",
                "idx_vessels_status_created",
                "idx_bookings_owner_status_created",
                "idx_bookings_status_created",
                "idx_coupons_owner_status_created",
                "idx_coupons_status_created",
                "idx_routes_active_created",
                "idx_pilots_status_created");
    }

    private void assertInvalidParameter(Request request) {
        RestTestClient.BodyContentSpec body = restTestClient.get()
                .uri(request.uri())
                .headers(headers -> headers.setBearerAuth(request.token()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody();
        body.jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST_PARAMETER")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.path").isEqualTo(request.uri().split("\\?")[0])
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.trace").doesNotExist();
    }

    private Principal createAdmin() throws Exception {
        var admin = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        return new Principal(admin.getId(), login(admin.getEmail(), ADMIN_PASSWORD));
    }

    private Principal registerOwner(String email) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Hardening Owner",
                        "email", email,
                        "password", OWNER_PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Long id = ((Number) json(body).get("id")).longValue();
        return new Principal(id, login(email, OWNER_PASSWORD));
    }

    private String login(String email, String password) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return json(body).get("accessToken").toString();
    }

    private void insertVessel(Long ownerId, String status, String suffix, Instant createdAt) {
        jdbcTemplate.update("""
                insert into vessels
                    (owner_id, name, registration_number, vessel_type, status,
                     created_at, updated_at, version)
                values (?, ?, ?, 'Container Ship', ?, ?, ?, 0)
                """, ownerId, "MV " + suffix, "REG-" + suffix, status,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] body) throws Exception {
        assertThat(body).isNotNull();
        return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), Map.class);
    }

    private record Principal(Long id, String token) {
    }

    private record Request(String uri, String token) {
    }
}
