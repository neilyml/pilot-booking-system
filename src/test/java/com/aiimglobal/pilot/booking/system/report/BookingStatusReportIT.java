package com.aiimglobal.pilot.booking.system.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

class BookingStatusReportIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void ownerDetailTracksBookingPaymentAndHistoricalPilotAcrossTheLifecycle() throws Exception {
        Principal owner = registerAndLoginOwner("tracking-owner@example.com", "Tracking Owner");
        Principal anotherOwner = registerAndLoginOwner("other-owner@example.com", "Other Owner");
        Principal admin = createAndLoginAdmin();
        Long vesselId = registerAndApproveVessel(owner.token(), admin.token(), "IMO-TRACKING");
        Long routeId = createRoute(admin.token(), "RT-TRACKING", "5000.00");
        Long bookingId = createBooking(
                owner.token(), vesselId, routeId, LocalDate.now().plusDays(10));

        ownerBooking(owner.token(), bookingId)
                .jsonPath("$.bookingNumber").isNotEmpty()
                .jsonPath("$.status").isEqualTo("PENDING_PAYMENT")
                .jsonPath("$.serviceDate").isEqualTo(LocalDate.now().plusDays(10).toString())
                .jsonPath("$.serviceFee").isEqualTo(5000.00)
                .jsonPath("$.vessel.name").isEqualTo("MV IMO-TRACKING")
                .jsonPath("$.vessel.registrationNumber").isEqualTo("IMO-TRACKING")
                .jsonPath("$.route.code").isEqualTo("RT-TRACKING")
                .jsonPath("$.route.name").isEqualTo("Tracking Route")
                .jsonPath("$.payment").isEmpty()
                .jsonPath("$.assignment").isEmpty();

        Coupon coupon = issueCoupon(admin.token(), owner.id(), "5000.00");
        pay(owner.token(), bookingId, coupon.code()).expectStatus().isCreated();
        ownerBooking(owner.token(), bookingId)
                .jsonPath("$.status").isEqualTo("PENDING_APPROVAL")
                .jsonPath("$.payment.status").isEqualTo("SUCCESS")
                .jsonPath("$.assignment").isEmpty();

        approveBooking(admin.token(), bookingId).expectStatus().isOk();
        ownerBooking(owner.token(), bookingId)
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.payment.status").isEqualTo("SUCCESS")
                .jsonPath("$.assignment").isEmpty();

        Long pilotId = createPilot(admin.token(), "MP-TRACKING", "Tracking Pilot");
        assignPilot(admin.token(), bookingId, pilotId).expectStatus().isCreated();
        ownerBooking(owner.token(), bookingId)
                .jsonPath("$.status").isEqualTo("ASSIGNED")
                .jsonPath("$.assignment.status").isEqualTo("ACTIVE")
                .jsonPath("$.assignment.pilotId").isEqualTo(pilotId.intValue())
                .jsonPath("$.assignment.pilotName").isEqualTo("Tracking Pilot");

        completeBooking(admin.token(), bookingId).expectStatus().isOk();
        ownerBooking(owner.token(), bookingId)
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.assignment.status").isEqualTo("COMPLETED")
                .jsonPath("$.assignment.pilotId").isEqualTo(pilotId.intValue())
                .jsonPath("$.assignment.pilotName").isEqualTo("Tracking Pilot")
                .jsonPath("$.completedAt").isNotEmpty();

        restTestClient.get()
                .uri("/api/v1/bookings/{id}", bookingId)
                .headers(headers -> headers.setBearerAuth(anotherOwner.token()))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_FOUND");
    }

    @Test
    void adminReportSupportsEveryFilterCombinationAndDeterministicPages() throws Exception {
        ReportFixture fixture = reportFixture();

        report(fixture.admin().token(), "")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.content[0].bookingNumber").isEqualTo("BKG-REPORT-C")
                .jsonPath("$.content[1].bookingNumber").isEqualTo("BKG-REPORT-B")
                .jsonPath("$.content[2].bookingNumber").isEqualTo("BKG-REPORT-A");

        report(fixture.admin().token(), "?status=PENDING_APPROVAL")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].bookingNumber").isEqualTo("BKG-REPORT-B")
                .jsonPath("$.content[0].ownerName").isEqualTo("Report Owner B")
                .jsonPath("$.content[0].bookingStatus").isEqualTo("PENDING_APPROVAL")
                .jsonPath("$.content[0].paymentStatus").isEqualTo("SUCCESS")
                .jsonPath("$.content[0].pilotName").isEmpty();

        report(fixture.admin().token(), "?from=" + fixture.firstDate() + "&to=" + fixture.secondDate())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].bookingNumber").isEqualTo("BKG-REPORT-B")
                .jsonPath("$.content[1].bookingNumber").isEqualTo("BKG-REPORT-A");

        report(fixture.admin().token(), "?routeId=" + fixture.firstRouteId())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].bookingNumber").isEqualTo("BKG-REPORT-C")
                .jsonPath("$.content[1].bookingNumber").isEqualTo("BKG-REPORT-A");

        report(fixture.admin().token(), "?pilotId=" + fixture.pilotId())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].bookingNumber").isEqualTo("BKG-REPORT-A")
                .jsonPath("$.content[0].ownerName").isEqualTo("Report Owner A")
                .jsonPath("$.content[0].vesselName").isEqualTo("MV Report A")
                .jsonPath("$.content[0].vesselRegistrationNumber").isEqualTo("IMO-REPORT-A")
                .jsonPath("$.content[0].routeCode").isEqualTo("RT-REPORT-A")
                .jsonPath("$.content[0].routeName").isEqualTo("Report Route A")
                .jsonPath("$.content[0].serviceDate").isEqualTo(fixture.firstDate().toString())
                .jsonPath("$.content[0].serviceFee").isEqualTo(5000.00)
                .jsonPath("$.content[0].bookingStatus").isEqualTo("ASSIGNED")
                .jsonPath("$.content[0].paymentStatus").isEqualTo("SUCCESS")
                .jsonPath("$.content[0].pilotName").isEqualTo("Report Pilot");

        String combined = "?status=ASSIGNED&from=" + fixture.firstDate()
                + "&to=" + fixture.firstDate()
                + "&routeId=" + fixture.firstRouteId()
                + "&pilotId=" + fixture.pilotId();
        report(fixture.admin().token(), combined)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].bookingNumber").isEqualTo("BKG-REPORT-A");

        report(fixture.admin().token(), "?status=COMPLETED")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isEmpty()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20);

        report(fixture.admin().token(), "?routeId=" + fixture.firstRouteId() + "&page=1&size=1")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].bookingNumber").isEqualTo("BKG-REPORT-A")
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(1)
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.first").isEqualTo(false)
                .jsonPath("$.last").isEqualTo(true);
    }

    @Test
    void ownerCannotAccessAdminBookingReport() throws Exception {
        Principal owner = registerAndLoginOwner("report-owner@example.com", "Report Owner");

        report(owner.token(), "")
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    void reportRejectsReversedDateRangeUsingTheExistingErrorShape() throws Exception {
        Principal admin = createAndLoginAdmin();
        LocalDate from = LocalDate.now().plusDays(10);
        LocalDate to = from.minusDays(1);

        report(admin.token(), "?from=" + from + "&to=" + to)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST_PARAMETER")
                .jsonPath("$.message").isEqualTo("From service date must not be after to service date.")
                .jsonPath("$.path").isEqualTo("/api/v1/admin/reports/bookings")
                .jsonPath("$.trace").doesNotExist();
    }

    private ReportFixture reportFixture() throws Exception {
        Principal admin = createAndLoginAdmin();
        Principal ownerA = registerAndLoginOwner("report-a@example.com", "Report Owner A");
        Principal ownerB = registerAndLoginOwner("report-b@example.com", "Report Owner B");
        Long routeA = insertRoute(admin.id(), "RT-REPORT-A", "Report Route A", "5000.00");
        Long routeB = insertRoute(admin.id(), "RT-REPORT-B", "Report Route B", "6000.00");
        Long vesselA = insertVessel(ownerA.id(), "MV Report A", "IMO-REPORT-A");
        Long vesselB = insertVessel(ownerB.id(), "MV Report B", "IMO-REPORT-B");
        LocalDate firstDate = LocalDate.now().plusDays(5);
        LocalDate secondDate = firstDate.plusDays(1);
        LocalDate thirdDate = secondDate.plusDays(1);
        Instant created = Instant.now().minusSeconds(30);
        Long bookingA = insertBooking(
                ownerA.id(), vesselA, routeA, firstDate, "5000.00", "ASSIGNED",
                "BKG-REPORT-A", created);
        Long bookingB = insertBooking(
                ownerB.id(), vesselB, routeB, secondDate, "6000.00", "PENDING_APPROVAL",
                "BKG-REPORT-B", created.plusSeconds(10));
        insertBooking(
                ownerA.id(), vesselA, routeA, thirdDate, "5000.00", "PENDING_PAYMENT",
                "BKG-REPORT-C", created.plusSeconds(20));
        insertPayment(bookingA, ownerA.id(), "5000.00", "PAY-REPORT-A");
        insertPayment(bookingB, ownerB.id(), "6000.00", "PAY-REPORT-B");
        Long pilotId = insertPilot("MP-REPORT", "Report Pilot");
        insertAssignment(bookingA, pilotId, admin.id(), firstDate);
        return new ReportFixture(admin, firstDate, secondDate, routeA, pilotId);
    }

    private RestTestClient.ResponseSpec report(String token, String query) {
        return restTestClient.get()
                .uri("/api/v1/admin/reports/bookings" + query)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private RestTestClient.BodyContentSpec ownerBooking(String token, Long bookingId) {
        return restTestClient.get()
                .uri("/api/v1/bookings/{id}", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    private Principal createAndLoginAdmin() throws Exception {
        var admin = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        return new Principal(admin.getId(), login(admin.getEmail(), ADMIN_PASSWORD));
    }

    private Principal registerAndLoginOwner(String email, String fullName) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", fullName,
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

    private Long registerAndApproveVessel(
            String ownerToken, String adminToken, String registrationNumber) throws Exception {
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
        Long vesselId = ((Number) json(body).get("id")).longValue();
        restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/approve", vesselId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk();
        return vesselId;
    }

    private Long createRoute(String adminToken, String code, String fee) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/routes")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "code", code,
                        "name", "Tracking Route",
                        "origin", "Outer Anchorage",
                        "destination", "Inner Harbour",
                        "serviceFee", fee))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
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
        byte[] body = restTestClient.post()
                .uri("/api/v1/bookings")
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "vesselId", vesselId,
                        "routeId", routeId,
                        "serviceDate", serviceDate.toString()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec pay(String token, Long bookingId, String couponCode) {
        return restTestClient.post()
                .uri("/api/v1/bookings/{id}/payments/coupon", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(Map.of("couponCode", couponCode))
                .exchange();
    }

    private RestTestClient.ResponseSpec approveBooking(String token, Long bookingId) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/approve", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private Long createPilot(String token, String employeeNumber, String name) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/pilots")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "employeeNumber", employeeNumber,
                        "name", name,
                        "phone", "+8801700000000",
                        "email", "tracking-pilot@example.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec assignPilot(String token, Long bookingId, Long pilotId) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/assign-pilot", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(Map.of("pilotId", pilotId))
                .exchange();
    }

    private RestTestClient.ResponseSpec completeBooking(String token, Long bookingId) {
        return restTestClient.post()
                .uri("/api/v1/admin/bookings/{id}/complete", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private Long insertRoute(Long adminId, String code, String name, String serviceFee) {
        return jdbcTemplate.queryForObject("""
                insert into routes
                    (code, name, origin, destination, service_fee, active,
                     created_by, created_at, updated_at, version)
                values (?, ?, 'Outer Anchorage', 'Inner Harbour', ?::numeric, true,
                        ?, now(), now(), 0)
                returning id
                """, Long.class, code, name, serviceFee, adminId);
    }

    private Long insertVessel(Long ownerId, String name, String registrationNumber) {
        return jdbcTemplate.queryForObject("""
                insert into vessels
                    (owner_id, name, registration_number, vessel_type, status,
                     created_at, updated_at, version)
                values (?, ?, ?, 'Container Ship', 'APPROVED', now(), now(), 0)
                returning id
                """, Long.class, ownerId, name, registrationNumber);
    }

    private Long insertBooking(
            Long ownerId,
            Long vesselId,
            Long routeId,
            LocalDate serviceDate,
            String serviceFee,
            String status,
            String bookingNumber,
            Instant createdAt) {
        return jdbcTemplate.queryForObject("""
                insert into bookings
                    (booking_number, requested_by, vessel_id, route_id, service_date,
                     service_fee, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?::numeric, ?, ?, ?, 0)
                returning id
                """, Long.class, bookingNumber, ownerId, vesselId, routeId, serviceDate,
                serviceFee, status, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void insertPayment(Long bookingId, Long payerId, String amount, String reference) {
        jdbcTemplate.update("""
                insert into payments
                    (booking_id, payer_id, amount, payment_method, status,
                     transaction_reference, paid_at, created_at)
                values (?, ?, ?::numeric, 'COUPON', 'SUCCESS', ?, now(), now())
                """, bookingId, payerId, amount, reference);
    }

    private Long insertPilot(String employeeNumber, String name) {
        return jdbcTemplate.queryForObject("""
                insert into pilots
                    (employee_number, name, status, created_at, updated_at, version)
                values (?, ?, 'ACTIVE', now(), now(), 0)
                returning id
                """, Long.class, employeeNumber, name);
    }

    private void insertAssignment(
            Long bookingId, Long pilotId, Long adminId, LocalDate serviceDate) {
        jdbcTemplate.update("""
                insert into booking_assignments
                    (booking_id, pilot_id, service_date, assigned_by, status, assigned_at)
                values (?, ?, ?, ?, 'ACTIVE', now())
                """, bookingId, pilotId, serviceDate, adminId);
    }

    private record Principal(Long id, String token) {
    }

    private record Coupon(Long id, String code) {
    }

    private record ReportFixture(
            Principal admin,
            LocalDate firstDate,
            LocalDate secondDate,
            Long firstRouteId,
            Long pilotId) {
    }
}
