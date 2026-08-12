package com.aiimglobal.pilot.booking.system.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

class BookingCompletionIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void completesBookingAndActiveAssignmentAtomically() throws Exception {
        Scenario scenario = assignedScenario("COMPLETE", LocalDate.now().plusDays(5));

        complete(scenario.adminToken(), scenario.bookingId())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.bookingId").isEqualTo(scenario.bookingId().intValue())
                .jsonPath("$.pilotId").isEqualTo(scenario.pilotId().intValue())
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.completedAt").isNotEmpty();

        Map<String, Object> booking = bookingRow(scenario.bookingId());
        Map<String, Object> assignment = assignmentRow(scenario.bookingId());
        assertThat(booking.get("status")).isEqualTo("COMPLETED");
        assertThat(booking.get("completed_at")).isNotNull();
        assertThat(assignment.get("status")).isEqualTo("COMPLETED");
        assertThat(assignment.get("completed_at")).isNotNull();
    }

    @Test
    void completedAssignmentNoLongerBlocksPilotAvailability() throws Exception {
        LocalDate serviceDate = LocalDate.now().plusDays(5);
        Scenario scenario = assignedScenario("AVAILABLE", serviceDate);
        complete(scenario.adminToken(), scenario.bookingId()).expectStatus().isOk();

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/admin/pilots/available")
                        .queryParam("serviceDate", serviceDate)
                        .build())
                .headers(headers -> headers.setBearerAuth(scenario.adminToken()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(scenario.pilotId().intValue())
                .jsonPath("$[1]").doesNotExist();
    }

    @Test
    void rejectsCompletionWhenBookingIsNotAssigned() throws Exception {
        Scenario scenario = approvedScenario("WRONG-STATE", LocalDate.now().plusDays(5));

        complete(scenario.adminToken(), scenario.bookingId())
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_ASSIGNED");

        assertThat(bookingRow(scenario.bookingId()).get("status")).isEqualTo("APPROVED");
    }

    @Test
    void rejectsMissingActiveAssignmentWithoutPartialCompletion() throws Exception {
        Scenario scenario = assignedScenario("MISSING", LocalDate.now().plusDays(5));
        jdbcTemplate.update("""
                update booking_assignments set status = 'CANCELLED' where booking_id = ?
                """, scenario.bookingId());

        complete(scenario.adminToken(), scenario.bookingId())
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACTIVE_ASSIGNMENT_NOT_FOUND");

        assertThat(bookingRow(scenario.bookingId()).get("status")).isEqualTo("ASSIGNED");
        assertThat(assignmentRow(scenario.bookingId()).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void ownerCannotCompleteBooking() throws Exception {
        Scenario scenario = assignedScenario("PROTECTED", LocalDate.now().plusDays(5));

        complete(scenario.ownerToken(), scenario.bookingId())
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        assertThat(bookingRow(scenario.bookingId()).get("status")).isEqualTo("ASSIGNED");
    }

    @Test
    void rollsBackBookingCompletionWhenAssignmentWriteFails() throws Exception {
        Scenario scenario = assignedScenario("ROLLBACK", LocalDate.now().plusDays(5));
        jdbcTemplate.execute("""
                alter table booking_assignments
                add constraint ck_test_reject_completed_assignment check (status <> 'COMPLETED')
                """);
        try {
            complete(scenario.adminToken(), scenario.bookingId()).expectStatus().isEqualTo(409);
        } finally {
            jdbcTemplate.execute("""
                    alter table booking_assignments drop constraint ck_test_reject_completed_assignment
                    """);
        }

        Map<String, Object> booking = bookingRow(scenario.bookingId());
        Map<String, Object> assignment = assignmentRow(scenario.bookingId());
        assertThat(booking.get("status")).isEqualTo("ASSIGNED");
        assertThat(booking.get("completed_at")).isNull();
        assertThat(assignment.get("status")).isEqualTo("ACTIVE");
        assertThat(assignment.get("completed_at")).isNull();
    }

    private Scenario assignedScenario(String suffix, LocalDate serviceDate) throws Exception {
        Scenario scenario = approvedScenario(suffix, serviceDate);
        restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/assign-pilot", scenario.bookingId())
                .headers(headers -> headers.setBearerAuth(scenario.adminToken()))
                .contentType(APPLICATION_JSON)
                .body(Map.of("pilotId", scenario.pilotId()))
                .exchange()
                .expectStatus().isCreated();
        return scenario;
    }

    private Scenario approvedScenario(String suffix, LocalDate serviceDate) throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(admin, "MP-" + suffix);
        Long vessel = approvedVessel(owner.token(), admin, "IMO-" + suffix);
        Long route = route(admin, "RT-" + suffix);
        byte[] body = restTestClient.post()
                .uri("/api/v1/bookings")
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "vesselId", vessel,
                        "routeId", route,
                        "serviceDate", serviceDate.toString()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Long booking = ((Number) json(body).get("id")).longValue();
        jdbcTemplate.update("update bookings set status = 'APPROVED' where id = ?", booking);
        return new Scenario(admin, owner.token(), booking, pilot);
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Completion Owner",
                        "email", email,
                        "password", OWNER_PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return new Owner(
                ((Number) json(body).get("id")).longValue(), login(email, OWNER_PASSWORD));
    }

    private String createAndLoginAdmin(String email) throws Exception {
        testDataFactory.createAdmin(email, ADMIN_PASSWORD);
        return login(email, ADMIN_PASSWORD);
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

    private Long createPilot(String admin, String employeeNumber) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/pilots")
                .headers(headers -> headers.setBearerAuth(admin))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "employeeNumber", employeeNumber,
                        "name", "Completion Pilot",
                        "phone", "+8801700000000",
                        "email", "completion-pilot@example.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private Long approvedVessel(String owner, String admin, String registration) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/vessels")
                .headers(headers -> headers.setBearerAuth(owner))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "name", "MV " + registration,
                        "registrationNumber", registration,
                        "vesselType", "Container Ship"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Long id = ((Number) json(body).get("id")).longValue();
        restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/approve", id)
                .headers(headers -> headers.setBearerAuth(admin))
                .exchange()
                .expectStatus().isOk();
        return id;
    }

    private Long route(String admin, String code) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/routes")
                .headers(headers -> headers.setBearerAuth(admin))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "code", code,
                        "name", "Completion Route",
                        "origin", "Outer Anchorage",
                        "destination", "Inner Harbour",
                        "serviceFee", "5000.00"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec complete(String token, Long booking) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/complete", booking)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private Map<String, Object> bookingRow(Long booking) {
        return jdbcTemplate.queryForMap("select * from bookings where id = ?", booking);
    }

    private Map<String, Object> assignmentRow(Long booking) {
        return jdbcTemplate.queryForMap(
                "select * from booking_assignments where booking_id = ?", booking);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] body) throws Exception {
        assertThat(body).isNotNull();
        return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), Map.class);
    }

    private record Owner(Long id, String token) {
    }

    private record Scenario(String adminToken, String ownerToken, Long bookingId, Long pilotId) {
    }
}
