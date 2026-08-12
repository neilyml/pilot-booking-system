package com.aiimglobal.pilot.booking.system.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

class AdminBookingReviewIT extends IntegrationTestBase {

    private static final String OWNER_PASSWORD = "owner-password";
    private static final String ADMIN_PASSWORD = "admin-password";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void listsOnlyBookingsMatchingReviewStatus() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        BookingFixture first = booking("owner-a@example.com", adminToken, "QUEUE-A");
        BookingFixture second = booking("owner-b@example.com", adminToken, "QUEUE-B");
        BookingFixture unpaid = booking("owner-c@example.com", adminToken, "QUEUE-C");
        markPaid(first);
        markPaid(second);

        restTestClient.get()
                .uri("/api/v1/admin/bookings?status=PENDING_APPROVAL")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(first.bookingId().intValue())
                .jsonPath("$[0].requestedByEmail").isEqualTo("owner-a@example.com")
                .jsonPath("$[0].status").isEqualTo("PENDING_APPROVAL")
                .jsonPath("$[0].payment.status").isEqualTo("SUCCESS")
                .jsonPath("$[1].id").isEqualTo(second.bookingId().intValue())
                .jsonPath("$[1].requestedByEmail").isEqualTo("owner-b@example.com")
                .jsonPath("$[2]").doesNotExist();

        assertThat(bookingRow(unpaid.bookingId()).get("status")).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void approvesPaidBookingAndRecordsReviewerWithoutAssigningPilot() throws Exception {
        Long adminId = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD).getId();
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);
        BookingFixture fixture = booking("owner@example.com", adminToken, "APPROVE");
        markPaid(fixture);

        approve(fixture.bookingId(), adminToken)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.reviewedById").isEqualTo(adminId.intValue())
                .jsonPath("$.reviewedByEmail").isEqualTo("admin@example.com")
                .jsonPath("$.reviewedAt").isNotEmpty()
                .jsonPath("$.rejectionReason").isEmpty()
                .jsonPath("$.assignment").isEmpty()
                .jsonPath("$.pilotId").doesNotExist();

        Map<String, Object> row = bookingRow(fixture.bookingId());
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(((Number) row.get("reviewed_by")).longValue()).isEqualTo(adminId);
        assertThat(row.get("reviewed_at")).isNotNull();
        assertThat(row.get("rejection_reason")).isNull();
    }

    @Test
    void rejectsPaidBookingWithReasonReviewerAndTime() throws Exception {
        Long adminId = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD).getId();
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);
        BookingFixture fixture = booking("owner@example.com", adminToken, "REJECT");
        markPaid(fixture);

        reject(fixture.bookingId(), adminToken, "Weather restrictions prevent service")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.reviewedById").isEqualTo(adminId.intValue())
                .jsonPath("$.reviewedAt").isNotEmpty()
                .jsonPath("$.rejectionReason").isEqualTo("Weather restrictions prevent service");

        Map<String, Object> row = bookingRow(fixture.bookingId());
        assertThat(row.get("status")).isEqualTo("REJECTED");
        assertThat(row.get("rejection_reason")).isEqualTo("Weather restrictions prevent service");
    }

    @Test
    void rejectsBlankReasonWithoutChangingBooking() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        BookingFixture fixture = booking("owner@example.com", adminToken, "BLANK");
        markPaid(fixture);

        reject(fixture.bookingId(), adminToken, " ")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("reason");

        assertThat(bookingRow(fixture.bookingId()).get("status")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void rejectsReviewOfUnpaidBooking() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        BookingFixture fixture = booking("owner@example.com", adminToken, "UNPAID");

        approve(fixture.bookingId(), adminToken)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_PENDING_APPROVAL");

        assertThat(bookingRow(fixture.bookingId()).get("status")).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void rejectsDecisionAfterBookingReviewIsFinal() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        BookingFixture fixture = booking("owner@example.com", adminToken, "FINAL");
        markPaid(fixture);
        approve(fixture.bookingId(), adminToken).expectStatus().isOk();

        reject(fixture.bookingId(), adminToken, "Changed decision")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_ALREADY_REVIEWED");

        assertThat(bookingRow(fixture.bookingId()).get("status")).isEqualTo("APPROVED");
    }

    @Test
    void ownerCannotReviewBookings() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        BookingFixture fixture = booking("owner@example.com", adminToken, "PROTECTED");
        markPaid(fixture);

        approve(fixture.bookingId(), fixture.ownerToken())
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        assertThat(bookingRow(fixture.bookingId()).get("status")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void concurrentApproveAndRejectAllowExactlyOneDecision() throws Exception {
        String setupAdmin = createAndLoginAdmin("setup-admin@example.com");
        String approvingAdmin = createAndLoginAdmin("approve-admin@example.com");
        String rejectingAdmin = createAndLoginAdmin("reject-admin@example.com");
        BookingFixture fixture = booking("owner@example.com", setupAdmin, "CONCURRENT");
        markPaid(fixture);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        List<EntityExchangeResult<byte[]>> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approve = executor.submit(() -> {
                ready.countDown();
                start.await();
                return approve(fixture.bookingId(), approvingAdmin).expectBody().returnResult();
            });
            var reject = executor.submit(() -> {
                ready.countDown();
                start.await();
                return reject(fixture.bookingId(), rejectingAdmin, "Concurrent rejection")
                        .expectBody()
                        .returnResult();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(approve.get(10, TimeUnit.SECONDS), reject.get(10, TimeUnit.SECONDS));
        }

        assertThat(results).extracting(EntityExchangeResult::getStatus)
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        EntityExchangeResult<byte[]> winner = results.stream()
                .filter(result -> result.getStatus().value() == 200)
                .findFirst()
                .orElseThrow();
        EntityExchangeResult<byte[]> loser = results.stream()
                .filter(result -> result.getStatus().value() == 409)
                .findFirst()
                .orElseThrow();
        String winningStatus = json(winner.getResponseBody()).get("status").toString();

        assertThat(json(loser.getResponseBody()).get("code"))
                .isIn("BOOKING_REVIEW_CONFLICT", "BOOKING_ALREADY_REVIEWED");
        Map<String, Object> row = bookingRow(fixture.bookingId());
        assertThat(row.get("status")).isEqualTo(winningStatus);
        assertThat(row.get("reviewed_by")).isNotNull();
        assertThat(row.get("reviewed_at")).isNotNull();
        if ("APPROVED".equals(winningStatus)) {
            assertThat(row.get("rejection_reason")).isNull();
        } else {
            assertThat(row.get("rejection_reason")).isEqualTo("Concurrent rejection");
        }
    }

    private BookingFixture booking(String email, String adminToken, String suffix) throws Exception {
        Owner owner = registerAndLoginOwner(email);
        Long vesselId = approvedVessel(owner.token(), adminToken, "IMO-" + suffix);
        Long routeId = route(adminToken, "RT-" + suffix);
        byte[] body = restTestClient.post()
                .uri("/api/v1/bookings")
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "vesselId", vesselId,
                        "routeId", routeId,
                        "serviceDate", LocalDate.now().plusDays(5).toString()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return new BookingFixture(
                ((Number) json(body).get("id")).longValue(), owner.id(), owner.token());
    }

    private void markPaid(BookingFixture fixture) {
        jdbcTemplate.update("""
                insert into payments
                    (booking_id, payer_id, amount, payment_method, status,
                     transaction_reference, paid_at, created_at)
                values (?, ?, 5000.00, 'COUPON', 'SUCCESS', ?, now(), now())
                """, fixture.bookingId(), fixture.ownerId(), "PAY-REVIEW-" + fixture.bookingId());
        jdbcTemplate.update(
                "update bookings set status = 'PENDING_APPROVAL' where id = ?", fixture.bookingId());
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Booking Owner",
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

    private Long approvedVessel(String ownerToken, String adminToken, String registrationNumber)
            throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/vessels")
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "name", "MV " + registrationNumber,
                        "registrationNumber", registrationNumber,
                        "vesselType", "Container Ship"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Long id = ((Number) json(body).get("id")).longValue();
        restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/approve", id)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk();
        return id;
    }

    private Long route(String adminToken, String code) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/routes")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "code", code,
                        "name", "Review Route",
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

    private RestTestClient.ResponseSpec approve(Long bookingId, String token) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/approve", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private RestTestClient.ResponseSpec reject(Long bookingId, String token, String reason) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/reject", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(Map.of("reason", reason))
                .exchange();
    }

    private Map<String, Object> bookingRow(Long bookingId) {
        return jdbcTemplate.queryForMap("select * from bookings where id = ?", bookingId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] body) throws Exception {
        assertThat(body).isNotNull();
        return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), Map.class);
    }

    private record Owner(Long id, String token) {
    }

    private record BookingFixture(Long bookingId, Long ownerId, String ownerToken) {
    }
}
