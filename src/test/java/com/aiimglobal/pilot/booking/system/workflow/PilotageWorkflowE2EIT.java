package com.aiimglobal.pilot.booking.system.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

class PilotageWorkflowE2EIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void completesTheFullPilotageJourneyOverLiveHttp() throws Exception {
        Owner owner = registerOwner("journey-owner@example.com");
        String ownerToken = login(owner.email(), OWNER_PASSWORD);
        Long vesselId = registerVessel(ownerToken, "IMO-JOURNEY");

        String adminToken = createAndLoginAdmin();
        approveVessel(adminToken, vesselId).expectStatus().isOk();
        Long routeId = createRoute(adminToken, "RT-JOURNEY", "5000.00");
        Coupon coupon = issueCoupon(adminToken, owner.id(), "7000.00");
        LocalDate serviceDate = LocalDate.now().plusDays(10);
        Long bookingId = createBooking(ownerToken, vesselId, routeId, serviceDate);

        updateRoute(adminToken, routeId, "RT-JOURNEY", "9000.00").expectStatus().isOk();
        pay(ownerToken, bookingId, coupon.code()).expectStatus().isCreated();
        approveBooking(adminToken, bookingId).expectStatus().isOk();
        Long pilotId = createPilot(adminToken, "MP-JOURNEY");

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/admin/pilots/available")
                        .queryParam("serviceDate", serviceDate)
                        .build())
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].id").isEqualTo(pilotId.intValue());

        assignPilot(adminToken, bookingId, pilotId).expectStatus().isCreated();
        completeBooking(adminToken, bookingId).expectStatus().isOk();

        restTestClient.get()
                .uri("/api/v1/bookings/{id}", bookingId)
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.vessel.status").isEqualTo("APPROVED")
                .jsonPath("$.serviceFee").isEqualTo(5000.00)
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.payment.status").isEqualTo("SUCCESS")
                .jsonPath("$.payment.amount").isEqualTo(5000.00)
                .jsonPath("$.assignment.status").isEqualTo("COMPLETED")
                .jsonPath("$.assignment.pilotId").isEqualTo(pilotId.intValue())
                .jsonPath("$.completedAt").isNotEmpty();

        assertThat(status("vessels", vesselId)).isEqualTo("APPROVED");
        assertThat(status("coupons", coupon.id())).isEqualTo("USED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from payments where booking_id = ?", String.class, bookingId))
                .isEqualTo("SUCCESS");
        assertThat(status("bookings", bookingId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from booking_assignments where booking_id = ?",
                String.class, bookingId)).isEqualTo("COMPLETED");
        assertThat(status("pilots", pilotId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "select service_fee from routes where id = ?", String.class, routeId))
                .isEqualTo("9000.00");
        assertThat(jdbcTemplate.queryForObject(
                "select service_fee from bookings where id = ?", String.class, bookingId))
                .isEqualTo("5000.00");
    }

    @Test
    void preventsASecondOwnerFromReadingOrUsingFirstOwnersResources() throws Exception {
        String adminToken = createAndLoginAdmin();
        Long routeId = createRoute(adminToken, "RT-ISOLATION", "5000.00");
        Owner firstOwner = registerAndLoginOwner("first-owner@example.com");
        Owner secondOwner = registerAndLoginOwner("second-owner@example.com");
        Long firstVessel = registerAndApproveVessel(
                firstOwner.token(), adminToken, "IMO-FIRST");
        Long secondVessel = registerAndApproveVessel(
                secondOwner.token(), adminToken, "IMO-SECOND");
        Long firstBooking = createBooking(
                firstOwner.token(), firstVessel, routeId, LocalDate.now().plusDays(8));
        Long secondBooking = createBooking(
                secondOwner.token(), secondVessel, routeId, LocalDate.now().plusDays(9));
        Coupon firstCoupon = issueCoupon(adminToken, firstOwner.id(), "5000.00");

        restTestClient.get()
                .uri("/api/v1/vessels/{id}", firstVessel)
                .headers(headers -> headers.setBearerAuth(secondOwner.token()))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VESSEL_NOT_FOUND");
        restTestClient.get()
                .uri("/api/v1/bookings/{id}", firstBooking)
                .headers(headers -> headers.setBearerAuth(secondOwner.token()))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_FOUND");
        createBookingRequest(secondOwner.token(), firstVessel, routeId, LocalDate.now().plusDays(12))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_VESSEL_NOT_FOUND");
        pay(secondOwner.token(), secondBooking, firstCoupon.code())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COUPON_NOT_FOUND");

        assertThat(status("bookings", firstBooking)).isEqualTo("PENDING_PAYMENT");
        assertThat(status("bookings", secondBooking)).isEqualTo("PENDING_PAYMENT");
        assertThat(status("coupons", firstCoupon.id())).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isZero();
    }

    @Test
    void rejectsWorkflowShortcutsWithoutMovingPastTheLastValidState() throws Exception {
        String adminToken = createAndLoginAdmin();
        Owner owner = registerAndLoginOwner("ordered-owner@example.com");
        Long vesselId = registerVessel(owner.token(), "IMO-ORDERED");
        Long routeId = createRoute(adminToken, "RT-ORDERED", "5000.00");
        LocalDate serviceDate = LocalDate.now().plusDays(10);

        createBookingRequest(owner.token(), vesselId, routeId, serviceDate)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_VESSEL_NOT_APPROVED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from bookings", Long.class)).isZero();

        approveVessel(adminToken, vesselId).expectStatus().isOk();
        Long bookingId = createBooking(owner.token(), vesselId, routeId, serviceDate);
        Long pilotId = createPilot(adminToken, "MP-ORDERED");

        approveBooking(adminToken, bookingId)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_PENDING_APPROVAL");
        assignPilot(adminToken, bookingId, pilotId)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_APPROVED");
        assertThat(status("bookings", bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from booking_assignments", Long.class)).isZero();

        Coupon coupon = issueCoupon(adminToken, owner.id(), "5000.00");
        pay(owner.token(), bookingId, coupon.code()).expectStatus().isCreated();
        pay(owner.token(), bookingId, coupon.code())
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_PENDING_PAYMENT");
        assertThat(status("bookings", bookingId)).isEqualTo("PENDING_APPROVAL");
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1L);

        approveBooking(adminToken, bookingId).expectStatus().isOk();
        completeBooking(adminToken, bookingId)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_ASSIGNED");
        assertThat(status("bookings", bookingId)).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from booking_assignments", Long.class)).isZero();
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        Owner owner = registerOwner(email);
        return new Owner(owner.id(), email, login(email, OWNER_PASSWORD));
    }

    private Owner registerOwner(String email) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Workflow Owner",
                        "email", email,
                        "password", OWNER_PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return new Owner(((Number) json(body).get("id")).longValue(), email, null);
    }

    private String createAndLoginAdmin() throws Exception {
        testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        return login("admin@example.com", ADMIN_PASSWORD);
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

    private Long registerVessel(String ownerToken, String registration) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/vessels")
                .headers(headers -> headers.setBearerAuth(ownerToken))
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
        return ((Number) json(body).get("id")).longValue();
    }

    private Long registerAndApproveVessel(
            String ownerToken, String adminToken, String registration) throws Exception {
        Long vesselId = registerVessel(ownerToken, registration);
        approveVessel(adminToken, vesselId).expectStatus().isOk();
        return vesselId;
    }

    private RestTestClient.ResponseSpec approveVessel(String adminToken, Long vesselId) {
        return restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/approve", vesselId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange();
    }

    private Long createRoute(String adminToken, String code, String fee) throws Exception {
        byte[] body = createRouteRequest(adminToken, code, fee)
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec updateRoute(
            String adminToken, Long routeId, String code, String fee) {
        return restTestClient.put()
                .uri("/api/v1/admin/routes/{id}", routeId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(routeBody(code, fee))
                .exchange();
    }

    private RestTestClient.ResponseSpec createRouteRequest(
            String adminToken, String code, String fee) {
        return restTestClient.post()
                .uri("/api/v1/admin/routes")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(routeBody(code, fee))
                .exchange();
    }

    private Map<String, String> routeBody(String code, String fee) {
        return Map.of(
                "code", code,
                "name", "Workflow Route",
                "origin", "Outer Anchorage",
                "destination", "Inner Harbour",
                "serviceFee", fee);
    }

    private Coupon issueCoupon(String adminToken, Long ownerId, String amount) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/coupons")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "ownerId", ownerId,
                        "amount", amount,
                        "expiresAt", Instant.now().plusSeconds(86_400).toString()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Map<String, Object> response = json(body);
        return new Coupon(
                ((Number) response.get("id")).longValue(), response.get("code").toString());
    }

    private Long createBooking(
            String ownerToken, Long vesselId, Long routeId, LocalDate serviceDate) throws Exception {
        byte[] body = createBookingRequest(ownerToken, vesselId, routeId, serviceDate)
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec createBookingRequest(
            String ownerToken, Long vesselId, Long routeId, LocalDate serviceDate) {
        return restTestClient.post()
                .uri("/api/v1/bookings")
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "vesselId", vesselId,
                        "routeId", routeId,
                        "serviceDate", serviceDate.toString()))
                .exchange();
    }

    private RestTestClient.ResponseSpec pay(String ownerToken, Long bookingId, String couponCode) {
        return restTestClient.post()
                .uri("/api/v1/bookings/{id}/payments/coupon", bookingId)
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of("couponCode", couponCode))
                .exchange();
    }

    private RestTestClient.ResponseSpec approveBooking(String adminToken, Long bookingId) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/approve", bookingId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange();
    }

    private Long createPilot(String adminToken, String employeeNumber) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/pilots")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "employeeNumber", employeeNumber,
                        "name", "Workflow Pilot",
                        "phone", "+8801700000000",
                        "email", employeeNumber.toLowerCase() + "@example.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec assignPilot(
            String adminToken, Long bookingId, Long pilotId) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/assign-pilot", bookingId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of("pilotId", pilotId))
                .exchange();
    }

    private RestTestClient.ResponseSpec completeBooking(String adminToken, Long bookingId) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/complete", bookingId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange();
    }

    private String status(String table, Long id) {
        return jdbcTemplate.queryForObject(
                "select status from " + table + " where id = ?", String.class, id);
    }

    private record Owner(Long id, String email, String token) {
    }

    private record Coupon(Long id, String code) {
    }
}
