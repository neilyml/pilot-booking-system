package com.aiimglobal.pilot.booking.system.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

class AssignmentSchedulingIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void availabilityIncludesOnlyActivePilotsFreeOnRequestedDate() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long freePilot = createPilot(admin, "MP-FREE");
        Long busyPilot = createPilot(admin, "MP-BUSY");
        Long inactivePilot = createPilot(admin, "MP-INACTIVE");
        LocalDate busyDate = LocalDate.now().plusDays(5);
        Long booking = approvedBooking(owner, admin, "AVAIL", busyDate);
        assign(admin, booking, busyPilot).expectStatus().isCreated();
        deactivatePilot(admin, inactivePilot).expectStatus().isOk();

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/admin/pilots/available")
                        .queryParam("serviceDate", busyDate)
                        .build())
                .headers(headers -> headers.setBearerAuth(admin))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].id").isEqualTo(freePilot.intValue())
                .jsonPath("$.content[1]").doesNotExist();

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/admin/pilots/available")
                        .queryParam("serviceDate", busyDate.plusDays(1))
                        .build())
                .headers(headers -> headers.setBearerAuth(admin))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].id").isEqualTo(busyPilot.intValue())
                .jsonPath("$.content[1].id").isEqualTo(freePilot.intValue())
                .jsonPath("$.content[2]").doesNotExist();
    }

    @Test
    void assignsActivePilotAndMovesApprovedBookingAtomically() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Long adminId = userId("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(admin, "MP-ASSIGN");
        LocalDate serviceDate = LocalDate.now().plusDays(5);
        Long booking = approvedBooking(owner, admin, "ASSIGN", serviceDate);

        assign(admin, booking, pilot)
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/admin/bookings/[0-9]+/assignments/[0-9]+")
                .expectBody()
                .jsonPath("$.bookingId").isEqualTo(booking.intValue())
                .jsonPath("$.pilotId").isEqualTo(pilot.intValue())
                .jsonPath("$.pilotEmployeeNumber").isEqualTo("MP-ASSIGN")
                .jsonPath("$.serviceDate").isEqualTo(serviceDate.toString())
                .jsonPath("$.assignedById").isEqualTo(adminId.intValue())
                .jsonPath("$.assignedByEmail").isEqualTo("admin@example.com")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.assignedAt").isNotEmpty()
                .jsonPath("$.completedAt").isEmpty();

        Map<String, Object> assignment = jdbcTemplate.queryForMap("select * from booking_assignments");
        assertThat(((Number) assignment.get("booking_id")).longValue()).isEqualTo(booking);
        assertThat(((Number) assignment.get("pilot_id")).longValue()).isEqualTo(pilot);
        assertThat(assignment.get("service_date").toString()).isEqualTo(serviceDate.toString());
        assertThat(((Number) assignment.get("assigned_by")).longValue()).isEqualTo(adminId);
        assertThat(assignment.get("status")).isEqualTo("ACTIVE");
        assertThat(bookingStatus(booking)).isEqualTo("ASSIGNED");

        restTestClient.get()
                .uri("/api/v1/bookings/{id}", booking)
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.assignment.status").isEqualTo("ACTIVE")
                .jsonPath("$.assignment.pilotId").isEqualTo(pilot.intValue());
    }

    @Test
    void rejectsBookingThatIsNotApproved() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(admin, "MP-WRONG-STATE");
        Long booking = pendingBooking(owner, admin, "WRONG-STATE", LocalDate.now().plusDays(5));

        assign(admin, booking, pilot)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_APPROVED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from booking_assignments", Long.class)).isZero();
    }

    @Test
    void rejectsInactivePilot() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(admin, "MP-INACTIVE-ASSIGN");
        deactivatePilot(admin, pilot).expectStatus().isOk();
        Long booking = approvedBooking(owner, admin, "INACTIVE", LocalDate.now().plusDays(5));

        assign(admin, booking, pilot)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PILOT_INACTIVE");

        assertThat(bookingStatus(booking)).isEqualTo("APPROVED");
    }

    @Test
    void rejectsBusyPilotAndSecondPilotForAssignedBooking() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long busyPilot = createPilot(admin, "MP-BUSY-CONFLICT");
        Long freePilot = createPilot(admin, "MP-SECOND");
        LocalDate date = LocalDate.now().plusDays(5);
        Long firstBooking = approvedBooking(owner, admin, "BUSY-ONE", date);
        Long secondBooking = approvedBooking(owner, admin, "BUSY-TWO", date);
        assign(admin, firstBooking, busyPilot).expectStatus().isCreated();

        assign(admin, secondBooking, busyPilot)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PILOT_NOT_AVAILABLE");
        assign(admin, firstBooking, freePilot)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_ALREADY_ASSIGNED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from booking_assignments", Long.class)).isEqualTo(1L);
        assertThat(bookingStatus(secondBooking)).isEqualTo("APPROVED");
    }

    @Test
    void ownerCannotAssignPilot() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(admin, "MP-PROTECTED");
        Long booking = approvedBooking(owner, admin, "PROTECTED", LocalDate.now().plusDays(5));

        assign(owner.token(), booking, pilot)
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    void concurrentBookingsCompetingForSamePilotAndDateHaveOneWinner() throws Exception {
        String adminOne = createAndLoginAdmin("admin-one@example.com");
        String adminTwo = createAndLoginAdmin("admin-two@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(adminOne, "MP-RACE-DATE");
        LocalDate date = LocalDate.now().plusDays(5);
        Long firstBooking = approvedBooking(owner, adminOne, "RACE-DATE-ONE", date);
        Long secondBooking = approvedBooking(owner, adminOne, "RACE-DATE-TWO", date);

        List<Integer> statuses = concurrent(
                () -> assignmentStatus(adminOne, firstBooking, pilot),
                () -> assignmentStatus(adminTwo, secondBooking, pilot));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject("select count(*) from booking_assignments", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bookings where status = 'ASSIGNED'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bookings where status = 'APPROVED'", Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentPilotsCompetingForSameBookingHaveOneWinner() throws Exception {
        String adminOne = createAndLoginAdmin("admin-one@example.com");
        String adminTwo = createAndLoginAdmin("admin-two@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long firstPilot = createPilot(adminOne, "MP-RACE-BOOKING-1");
        Long secondPilot = createPilot(adminOne, "MP-RACE-BOOKING-2");
        Long booking = approvedBooking(
                owner, adminOne, "RACE-BOOKING", LocalDate.now().plusDays(5));

        List<Integer> statuses = concurrent(
                () -> assignmentStatus(adminOne, booking, firstPilot),
                () -> assignmentStatus(adminTwo, booking, secondPilot));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject("select count(*) from booking_assignments", Long.class)).isEqualTo(1L);
    }

    @Test
    void databasePartialIndexesRejectDuplicateActiveScheduling() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Long adminId = userId("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long firstPilot = createPilot(admin, "MP-DB-1");
        Long secondPilot = createPilot(admin, "MP-DB-2");
        LocalDate date = LocalDate.now().plusDays(5);
        Long firstBooking = approvedBooking(owner, admin, "DB-ONE", date);
        Long secondBooking = approvedBooking(owner, admin, "DB-TWO", date);
        assign(admin, firstBooking, firstPilot).expectStatus().isCreated();

        assertThatThrownBy(() -> insertActiveAssignment(
                        firstBooking, secondPilot, date, adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertActiveAssignment(
                        secondBooking, firstPilot, date, adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void assignedPilotCannotBeDeactivated() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(admin, "MP-ACTIVE-WORK");
        Long booking = approvedBooking(owner, admin, "ACTIVE-WORK", LocalDate.now().plusDays(5));
        assign(admin, booking, pilot).expectStatus().isCreated();

        deactivatePilot(admin, pilot)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PILOT_HAS_ACTIVE_ASSIGNMENT");

        assertThat(jdbcTemplate.queryForObject(
                "select status from pilots where id = ?", String.class, pilot)).isEqualTo("ACTIVE");
    }

    @Test
    void rollsBackAssignmentWhenBookingTransitionFails() throws Exception {
        String admin = createAndLoginAdmin("admin@example.com");
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long pilot = createPilot(admin, "MP-ROLLBACK");
        Long booking = approvedBooking(owner, admin, "ROLLBACK", LocalDate.now().plusDays(5));
        jdbcTemplate.execute("""
                alter table bookings
                add constraint ck_test_reject_assigned_status check (status <> 'ASSIGNED')
                """);
        try {
            assign(admin, booking, pilot).expectStatus().isEqualTo(409);
        } finally {
            jdbcTemplate.execute("""
                    alter table bookings drop constraint ck_test_reject_assigned_status
                    """);
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from booking_assignments", Long.class)).isZero();
        assertThat(bookingStatus(booking)).isEqualTo("APPROVED");
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Assignment Owner",
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
                        "name", "Pilot " + employeeNumber,
                        "phone", "+8801700000000",
                        "email", employeeNumber.toLowerCase() + "@example.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private Long approvedBooking(
            Owner owner, String admin, String suffix, LocalDate serviceDate) throws Exception {
        Long booking = pendingBooking(owner, admin, suffix, serviceDate);
        jdbcTemplate.update("update bookings set status = 'APPROVED' where id = ?", booking);
        return booking;
    }

    private Long pendingBooking(
            Owner owner, String admin, String suffix, LocalDate serviceDate) throws Exception {
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
                        "name", "Assignment Route",
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

    private RestTestClient.ResponseSpec assign(String token, Long booking, Long pilot) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/assign-pilot", booking)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(Map.of("pilotId", pilot))
                .exchange();
    }

    private RestTestClient.ResponseSpec deactivatePilot(String token, Long pilot) {
        return restTestClient.post()
                .uri("/api/v1/admin/pilots/{id}/deactivate", pilot)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private int assignmentStatus(String token, Long booking, Long pilot) {
        return assign(token, booking, pilot)
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    private List<Integer> concurrent(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            Future<Integer> firstResult = executor.submit(synchronizedCall(first, ready, start));
            Future<Integer> secondResult = executor.submit(synchronizedCall(second, ready, start));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> synchronizedCall(
            Callable<Integer> operation, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            return operation.call();
        };
    }

    private void insertActiveAssignment(
            Long booking, Long pilot, LocalDate date, Long admin) {
        jdbcTemplate.update("""
                insert into booking_assignments
                    (booking_id, pilot_id, service_date, assigned_by, status, assigned_at, completed_at)
                values (?, ?, ?, ?, 'ACTIVE', now(), null)
                """, booking, pilot, date, admin);
    }

    private Long userId(String email) {
        return jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, email);
    }

    private String bookingStatus(Long booking) {
        return jdbcTemplate.queryForObject(
                "select status from bookings where id = ?", String.class, booking);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] body) throws Exception {
        assertThat(body).isNotNull();
        return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), Map.class);
    }

    private record Owner(Long id, String token) {
    }
}
