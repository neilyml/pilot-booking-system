package com.aiimglobal.pilot.booking.system.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

class DashboardIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void returnsOwnerCountsAndCurrentlyUsableCouponValueWithStrictIsolation() throws Exception {
        Principal admin = createAdmin();
        Principal owner = registerOwner("owner-a@example.com");
        Principal anotherOwner = registerOwner("owner-b@example.com");

        Long pendingVessel = insertVessel(owner.id(), "PENDING", "OWNER-A-1");
        insertVessel(owner.id(), "PENDING", "OWNER-A-2");
        insertVessel(owner.id(), "APPROVED", "OWNER-A-3");
        insertVessel(owner.id(), "REJECTED", "OWNER-A-4");
        Long otherVessel = insertVessel(anotherOwner.id(), "APPROVED", "OWNER-B-1");
        Long route = insertRoute(admin.id(), "DASH-OWNER");

        insertBooking(owner.id(), pendingVessel, route, "PENDING_PAYMENT", "OWNER-A-BOOK-1");
        insertBooking(owner.id(), pendingVessel, route, "APPROVED", "OWNER-A-BOOK-2");
        insertBooking(owner.id(), pendingVessel, route, "APPROVED", "OWNER-A-BOOK-3");
        insertBooking(anotherOwner.id(), otherVessel, route, "COMPLETED", "OWNER-B-BOOK-1");

        insertCoupon(owner.id(), admin.id(), "ACTIVE", "100.00", false, "OWNER-A-ACTIVE");
        insertCoupon(owner.id(), admin.id(), "ACTIVE", "200.00", true, "OWNER-A-ELAPSED");
        insertCoupon(owner.id(), admin.id(), "USED", "300.00", false, "OWNER-A-USED");
        insertCoupon(owner.id(), admin.id(), "EXPIRED", "400.00", true, "OWNER-A-EXPIRED");
        insertCoupon(owner.id(), admin.id(), "CANCELLED", "500.00", false, "OWNER-A-CANCELLED");
        insertCoupon(anotherOwner.id(), admin.id(), "ACTIVE", "999.00", false, "OWNER-B-ACTIVE");

        restTestClient.get()
                .uri("/api/v1/dashboard")
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.vesselCounts.PENDING").isEqualTo(2)
                .jsonPath("$.vesselCounts.APPROVED").isEqualTo(1)
                .jsonPath("$.vesselCounts.REJECTED").isEqualTo(1)
                .jsonPath("$.bookingCounts.PENDING_PAYMENT").isEqualTo(1)
                .jsonPath("$.bookingCounts.APPROVED").isEqualTo(2)
                .jsonPath("$.bookingCounts.COMPLETED").isEqualTo(0)
                .jsonPath("$.couponCounts.ACTIVE").isEqualTo(2)
                .jsonPath("$.couponCounts.USED").isEqualTo(1)
                .jsonPath("$.couponCounts.EXPIRED").isEqualTo(1)
                .jsonPath("$.couponCounts.CANCELLED").isEqualTo(1)
                .jsonPath("$.availableCouponCount").isEqualTo(1)
                .jsonPath("$.availableCouponValue").isEqualTo(100.00);
    }

    @Test
    void returnsSystemWideAdminCountsAndActualSuccessfulRedemptionValue() throws Exception {
        Principal admin = createAdmin();
        Principal owner = registerOwner("owner@example.com");
        Long vessel = insertVessel(owner.id(), "APPROVED", "ADMIN-1");
        insertVessel(owner.id(), "REJECTED", "ADMIN-2");
        Long route = insertRoute(admin.id(), "DASH-ADMIN");
        Long successfulBooking = insertBooking(
                owner.id(), vessel, route, "PENDING_APPROVAL", "ADMIN-BOOK-SUCCESS");
        Long failedBooking = insertBooking(
                owner.id(), vessel, route, "PENDING_PAYMENT", "ADMIN-BOOK-FAILED");
        Long successfulCoupon = insertCoupon(
                owner.id(), admin.id(), "USED", "9000.00", false, "ADMIN-SUCCESS");
        Long failedCoupon = insertCoupon(
                owner.id(), admin.id(), "USED", "8000.00", false, "ADMIN-FAILED");
        insertPilot("ACTIVE", "DASH-PILOT-1");
        insertPilot("ACTIVE", "DASH-PILOT-2");
        insertPilot("INACTIVE", "DASH-PILOT-3");
        Long successfulPayment = insertPayment(
                successfulBooking, owner.id(), "SUCCESS", "5000.00", "DASH-PAY-SUCCESS");
        Long failedPayment = insertPayment(
                failedBooking, owner.id(), "FAILED", "700.00", "DASH-PAY-FAILED");
        insertRedemption(successfulCoupon, successfulPayment, "5000.00");
        insertRedemption(failedCoupon, failedPayment, "700.00");

        restTestClient.get()
                .uri("/api/v1/admin/dashboard")
                .headers(headers -> headers.setBearerAuth(admin.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.vesselCounts.APPROVED").isEqualTo(1)
                .jsonPath("$.vesselCounts.REJECTED").isEqualTo(1)
                .jsonPath("$.bookingCounts.PENDING_PAYMENT").isEqualTo(1)
                .jsonPath("$.bookingCounts.PENDING_APPROVAL").isEqualTo(1)
                .jsonPath("$.couponCounts.USED").isEqualTo(2)
                .jsonPath("$.pilotCounts.ACTIVE").isEqualTo(2)
                .jsonPath("$.pilotCounts.INACTIVE").isEqualTo(1)
                .jsonPath("$.redeemedValue").isEqualTo(5000.00);
    }

    @Test
    void deniesOwnerAccessToAdminDashboard() throws Exception {
        Principal owner = registerOwner("owner@example.com");

        restTestClient.get()
                .uri("/api/v1/admin/dashboard")
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
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
                        "fullName", "Dashboard Owner",
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

    private Long insertVessel(Long ownerId, String status, String suffix) {
        return jdbcTemplate.queryForObject("""
                insert into vessels
                    (owner_id, name, registration_number, vessel_type, status,
                     created_at, updated_at, version)
                values (?, ?, ?, 'Container Ship', ?, now(), now(), 0)
                returning id
                """, Long.class, ownerId, "MV " + suffix, "REG-" + suffix, status);
    }

    private Long insertRoute(Long adminId, String code) {
        return jdbcTemplate.queryForObject("""
                insert into routes
                    (code, name, origin, destination, service_fee, active,
                     created_by, created_at, updated_at, version)
                values (?, 'Dashboard Route', 'Outer Anchorage', 'Inner Harbour',
                        5000.00, true, ?, now(), now(), 0)
                returning id
                """, Long.class, code, adminId);
    }

    private Long insertBooking(
            Long ownerId, Long vesselId, Long routeId, String status, String suffix) {
        return jdbcTemplate.queryForObject("""
                insert into bookings
                    (booking_number, requested_by, vessel_id, route_id, service_date,
                     service_fee, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 5000.00, ?, now(), now(), 0)
                returning id
                """, Long.class, "BKG-" + suffix, ownerId, vesselId, routeId,
                LocalDate.now().plusDays(5), status);
    }

    private Long insertCoupon(
            Long ownerId,
            Long adminId,
            String status,
            String amount,
            boolean elapsed,
            String suffix) {
        Instant createdAt = elapsed ? Instant.now().minusSeconds(172_800) : Instant.now();
        Instant expiresAt = elapsed
                ? Instant.now().minusSeconds(86_400)
                : Instant.now().plusSeconds(86_400);
        return jdbcTemplate.queryForObject("""
                insert into coupons
                    (code, owner_id, amount, status, expires_at, issued_by,
                     created_at, used_at, version)
                values (?, ?, ?::numeric, ?, ?, ?, ?,
                        case when ? = 'USED' then now() else null end, 0)
                returning id
                """, Long.class, "CPN-" + suffix, ownerId, amount, status, Timestamp.from(expiresAt),
                adminId, Timestamp.from(createdAt), status);
    }

    private void insertPilot(String status, String employeeNumber) {
        jdbcTemplate.update("""
                insert into pilots
                    (employee_number, name, status, created_at, updated_at, version)
                values (?, 'Dashboard Pilot', ?, now(), now(), 0)
                """, employeeNumber, status);
    }

    private Long insertPayment(
            Long bookingId, Long payerId, String status, String amount, String reference) {
        return jdbcTemplate.queryForObject("""
                insert into payments
                    (booking_id, payer_id, amount, payment_method, status,
                     transaction_reference, paid_at, created_at)
                values (?, ?, ?::numeric, 'COUPON', ?, ?,
                        case when ? = 'SUCCESS' then now() else null end, now())
                returning id
                """, Long.class, bookingId, payerId, amount, status, reference, status);
    }

    private void insertRedemption(Long couponId, Long paymentId, String amount) {
        jdbcTemplate.update("""
                insert into coupon_redemptions
                    (coupon_id, payment_id, amount_redeemed, redeemed_at)
                values (?, ?, ?::numeric, now())
                """, couponId, paymentId, amount);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] body) throws Exception {
        assertThat(body).isNotNull();
        return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), Map.class);
    }

    private record Principal(Long id, String token) {
    }
}
