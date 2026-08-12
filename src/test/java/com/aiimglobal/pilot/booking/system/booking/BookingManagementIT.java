package com.aiimglobal.pilot.booking.system.booking;

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

class BookingManagementIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";
    private static final String BOOKINGS_PATH = "/api/v1/bookings";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void createsPendingPaymentBookingWithUniqueServerValues() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();
        Long vesselId = createVessel(owner, "IMO-BOOK-1", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-BOOK-1", "5000.00");
        LocalDate serviceDate = LocalDate.now().plusDays(5);

        byte[] firstBody = createBooking(owner.token(), """
                {
                  "vesselId": %d,
                  "routeId": %d,
                  "serviceDate": "%s",
                  "bookingNumber": "CLIENT-FORGED",
                  "requestedById": 999999,
                  "serviceFee": 1.00,
                  "status": "COMPLETED",
                  "reviewedBy": 999999,
                  "pilotId": 999999,
                  "completedAt": "2020-01-01T00:00:00Z"
                }
                """.formatted(vesselId, routeId, serviceDate))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/bookings/[0-9]+")
                .expectBody()
                .jsonPath("$.bookingNumber").value(value ->
                        assertThat(value.toString()).matches("BKG-[0-9A-F-]{36}"))
                .jsonPath("$.requestedById").isEqualTo(owner.id().intValue())
                .jsonPath("$.serviceDate").isEqualTo(serviceDate.toString())
                .jsonPath("$.serviceFee").isEqualTo(5000.00)
                .jsonPath("$.status").isEqualTo("PENDING_PAYMENT")
                .jsonPath("$.vessel.id").isEqualTo(vesselId.intValue())
                .jsonPath("$.route.id").isEqualTo(routeId.intValue())
                .jsonPath("$.payment").isEmpty()
                .jsonPath("$.assignment").isEmpty()
                .jsonPath("$.createdAt").isNotEmpty()
                .jsonPath("$.updatedAt").isNotEmpty()
                .jsonPath("$.completedAt").isEmpty()
                .jsonPath("$.requestedBy").doesNotExist()
                .jsonPath("$.version").doesNotExist()
                .returnResult()
                .getResponseBody();
        byte[] secondBody = createBooking(owner.token(), validBooking(
                        vesselId, routeId, serviceDate.plusDays(1)))
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(json(firstBody).get("bookingNumber"))
                .isNotEqualTo(json(secondBody).get("bookingNumber"));
        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "select * from bookings where booking_number = ?", json(firstBody).get("bookingNumber"));
        assertThat(((Number) persisted.get("requested_by")).longValue()).isEqualTo(owner.id());
        assertThat(persisted.get("service_fee").toString()).isEqualTo("5000.00");
        assertThat(persisted.get("status")).isEqualTo("PENDING_PAYMENT");
        assertThat(persisted.get("reviewed_by")).isNull();
        assertThat(persisted.get("completed_at")).isNull();
    }

    @Test
    void preservesHistoricalFeeWhenRoutePriceChanges() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();
        Long vesselId = createVessel(owner, "IMO-HISTORY", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-HISTORY", "5000.00");
        Long bookingId = createdBookingId(owner.token(), validBooking(
                vesselId, routeId, LocalDate.now().plusDays(5)));

        updateRoute(adminToken, routeId, "RT-HISTORY", "7000.00")
                .expectStatus().isOk();

        getBooking(owner.token(), bookingId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceFee").isEqualTo(5000.00);
        assertThat(jdbcTemplate.queryForObject(
                "select service_fee from bookings where id = ?", String.class, bookingId))
                .isEqualTo("5000.00");
    }

    @Test
    void rejectsPendingAndRejectedVesselsWithoutCreatingBookings() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();
        Long routeId = createRoute(adminToken, "RT-VESSEL-STATE", "5000.00");
        Long pendingVesselId = createVessel(owner, "IMO-PENDING", "PENDING", adminToken);
        Long rejectedVesselId = createVessel(owner, "IMO-REJECTED", "REJECTED", adminToken);

        createBooking(owner.token(), validBooking(
                        pendingVesselId, routeId, LocalDate.now().plusDays(5)))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_VESSEL_NOT_APPROVED");
        createBooking(owner.token(), validBooking(
                        rejectedVesselId, routeId, LocalDate.now().plusDays(6)))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_VESSEL_NOT_APPROVED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from bookings", Long.class)).isZero();
    }

    @Test
    void hidesAnotherOwnersVesselAsNotFound() throws Exception {
        Owner ownerA = registerAndLoginOwner("owner-a@example.com");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");
        String adminToken = createAndLoginAdmin();
        Long ownerBVessel = createVessel(ownerB, "IMO-PRIVATE", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-PRIVATE", "5000.00");

        createBooking(ownerA.token(), validBooking(
                        ownerBVessel, routeId, LocalDate.now().plusDays(5)))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_VESSEL_NOT_FOUND");

        assertThat(jdbcTemplate.queryForObject("select count(*) from bookings", Long.class)).isZero();
    }

    @Test
    void rejectsInactiveRouteWithoutCreatingBooking() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();
        Long vesselId = createVessel(owner, "IMO-INACTIVE-ROUTE", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-INACTIVE", "5000.00");
        restTestClient.post()
                .uri("/api/v1/admin/routes/{id}/deactivate", routeId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk();

        createBooking(owner.token(), validBooking(
                        vesselId, routeId, LocalDate.now().plusDays(5)))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_ROUTE_INACTIVE");

        assertThat(jdbcTemplate.queryForObject("select count(*) from bookings", Long.class)).isZero();
    }

    @Test
    void rejectsTodayAndPastServiceDates() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();
        Long vesselId = createVessel(owner, "IMO-DATE", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-DATE", "5000.00");

        createBooking(owner.token(), validBooking(vesselId, routeId, LocalDate.now()))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("serviceDate");
        createBooking(owner.token(), validBooking(
                        vesselId, routeId, LocalDate.now().minusDays(1)))
                .expectStatus().isBadRequest();

        assertThat(jdbcTemplate.queryForObject("select count(*) from bookings", Long.class)).isZero();
    }

    @Test
    void listsOnlyAuthenticatedOwnersBookings() throws Exception {
        Owner ownerA = registerAndLoginOwner("owner-a@example.com");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");
        String adminToken = createAndLoginAdmin();
        Long ownerAVessel = createVessel(ownerA, "IMO-LIST-A", "APPROVED", adminToken);
        Long ownerBVessel = createVessel(ownerB, "IMO-LIST-B", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-LIST", "5000.00");
        Long ownerAFirst = createdBookingId(ownerA.token(), validBooking(
                ownerAVessel, routeId, LocalDate.now().plusDays(5)));
        createdBookingId(ownerB.token(), validBooking(
                ownerBVessel, routeId, LocalDate.now().plusDays(6)));
        Long ownerASecond = createdBookingId(ownerA.token(), validBooking(
                ownerAVessel, routeId, LocalDate.now().plusDays(7)));

        restTestClient.get()
                .uri(BOOKINGS_PATH)
                .headers(headers -> headers.setBearerAuth(ownerA.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(ownerAFirst.intValue())
                .jsonPath("$[0].requestedById").isEqualTo(ownerA.id().intValue())
                .jsonPath("$[1].id").isEqualTo(ownerASecond.intValue())
                .jsonPath("$[1].requestedById").isEqualTo(ownerA.id().intValue())
                .jsonPath("$[2]").doesNotExist();
    }

    @Test
    void returnsOwnedBookingDetailWithShallowOperationalSummaries() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();
        Long vesselId = createVessel(owner, "IMO-DETAIL", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-DETAIL", "5000.00");
        LocalDate serviceDate = LocalDate.now().plusDays(5);
        Long bookingId = createdBookingId(owner.token(), validBooking(vesselId, routeId, serviceDate));

        getBooking(owner.token(), bookingId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(bookingId.intValue())
                .jsonPath("$.status").isEqualTo("PENDING_PAYMENT")
                .jsonPath("$.serviceDate").isEqualTo(serviceDate.toString())
                .jsonPath("$.serviceFee").isEqualTo(5000.00)
                .jsonPath("$.vessel.id").isEqualTo(vesselId.intValue())
                .jsonPath("$.vessel.name").isEqualTo("MV IMO-DETAIL")
                .jsonPath("$.vessel.registrationNumber").isEqualTo("IMO-DETAIL")
                .jsonPath("$.vessel.status").isEqualTo("APPROVED")
                .jsonPath("$.route.id").isEqualTo(routeId.intValue())
                .jsonPath("$.route.code").isEqualTo("RT-DETAIL")
                .jsonPath("$.route.origin").isEqualTo("Outer Anchorage")
                .jsonPath("$.route.destination").isEqualTo("Inner Harbour")
                .jsonPath("$.payment").isEmpty()
                .jsonPath("$.assignment").isEmpty()
                .jsonPath("$.requestedBy").doesNotExist()
                .jsonPath("$.reviewedBy").doesNotExist();
    }

    @Test
    void hidesAnotherOwnersBookingDetailAsNotFound() throws Exception {
        Owner ownerA = registerAndLoginOwner("owner-a@example.com");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");
        String adminToken = createAndLoginAdmin();
        Long ownerBVessel = createVessel(ownerB, "IMO-DETAIL-PRIVATE", "APPROVED", adminToken);
        Long routeId = createRoute(adminToken, "RT-DETAIL-PRIVATE", "5000.00");
        Long ownerBBooking = createdBookingId(ownerB.token(), validBooking(
                ownerBVessel, routeId, LocalDate.now().plusDays(5)));

        getBooking(ownerA.token(), ownerBBooking)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_FOUND");
    }

    @Test
    void adminWithoutOwnerRoleCannotCreateBooking() throws Exception {
        String adminToken = createAndLoginAdmin();

        createBooking(adminToken, validBooking(1L, 1L, LocalDate.now().plusDays(5)))
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        byte[] responseBody = restTestClient.post()
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
        Long ownerId = ((Number) json(responseBody).get("id")).longValue();
        return new Owner(ownerId, email, login(email, OWNER_PASSWORD));
    }

    private String createAndLoginAdmin() throws Exception {
        testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        return login("admin@example.com", ADMIN_PASSWORD);
    }

    private String login(String email, String password) throws Exception {
        byte[] responseBody = restTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return json(responseBody).get("accessToken").toString();
    }

    private Long createVessel(Owner owner, String registrationNumber, String finalStatus, String adminToken)
            throws Exception {
        byte[] responseBody = restTestClient.post()
                .uri("/api/v1/vessels")
                .headers(headers -> headers.setBearerAuth(owner.token()))
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
        Long vesselId = ((Number) json(responseBody).get("id")).longValue();
        if ("APPROVED".equals(finalStatus)) {
            restTestClient.post()
                    .uri("/api/v1/admin/vessels/{id}/approve", vesselId)
                    .headers(headers -> headers.setBearerAuth(adminToken))
                    .exchange()
                    .expectStatus().isOk();
        } else if ("REJECTED".equals(finalStatus)) {
            restTestClient.post()
                    .uri("/api/v1/admin/vessels/{id}/reject", vesselId)
                    .headers(headers -> headers.setBearerAuth(adminToken))
                    .contentType(APPLICATION_JSON)
                    .body(Map.of("reason", "Not eligible for service"))
                    .exchange()
                    .expectStatus().isOk();
        }
        return vesselId;
    }

    private Long createRoute(String adminToken, String code, String fee) throws Exception {
        byte[] responseBody = restTestClient.post()
                .uri("/api/v1/admin/routes")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(routeRequest(code, fee))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(responseBody).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec updateRoute(
            String adminToken, Long routeId, String code, String fee) {
        return restTestClient.put()
                .uri("/api/v1/admin/routes/{id}", routeId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(routeRequest(code, fee))
                .exchange();
    }

    private Map<String, Object> routeRequest(String code, String fee) {
        return Map.of(
                "code", code,
                "name", "Harbour Transit",
                "origin", "Outer Anchorage",
                "destination", "Inner Harbour",
                "serviceFee", fee);
    }

    private Map<String, Object> validBooking(Long vesselId, Long routeId, LocalDate serviceDate) {
        return Map.of(
                "vesselId", vesselId,
                "routeId", routeId,
                "serviceDate", serviceDate.toString());
    }

    private RestTestClient.ResponseSpec createBooking(String token, Object request) {
        return restTestClient.post()
                .uri(BOOKINGS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private Long createdBookingId(String token, Object request) throws Exception {
        byte[] responseBody = createBooking(token, request)
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(responseBody).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec getBooking(String token, Long bookingId) {
        return restTestClient.get()
                .uri(BOOKINGS_PATH + "/{id}", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] responseBody) throws Exception {
        assertThat(responseBody).isNotNull();
        return objectMapper.readValue(new String(responseBody, StandardCharsets.UTF_8), Map.class);
    }

    private record Owner(Long id, String email, String token) {
    }
}
