package com.aiimglobal.pilot.booking.system.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

class CouponPaymentIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void redeemsExactValueCouponAndMovesBookingAtomically() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "5000.00", "EXACT");

        pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", scenario.couponCode()))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/payments/[0-9]+")
                .expectBody()
                .jsonPath("$.bookingId").isEqualTo(scenario.bookingId().intValue())
                .jsonPath("$.payerId").isEqualTo(scenario.owner().id().intValue())
                .jsonPath("$.amount").isEqualTo(5000.00)
                .jsonPath("$.paymentMethod").isEqualTo("COUPON")
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.transactionReference").value(value ->
                        assertThat(value.toString()).matches("PAY-[0-9A-F-]{36}"))
                .jsonPath("$.couponCode").isEqualTo(scenario.couponCode())
                .jsonPath("$.amountRedeemed").isEqualTo(5000.00)
                .jsonPath("$.paidAt").isNotEmpty()
                .jsonPath("$.redeemedAt").isNotEmpty()
                .jsonPath("$.payer").doesNotExist();

        assertSuccessfulState(scenario, "5000.00");
        restTestClient.get()
                .uri("/api/v1/bookings/{id}", scenario.bookingId())
                .headers(headers -> headers.setBearerAuth(scenario.owner().token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING_APPROVAL")
                .jsonPath("$.payment.status").isEqualTo("SUCCESS")
                .jsonPath("$.payment.amount").isEqualTo(5000.00)
                .jsonPath("$.assignment").isEmpty();
    }

    @Test
    void consumesHigherValueCouponWithoutBalanceOrChange() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "7000.00", "HIGHER");

        pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", scenario.couponCode()))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(5000.00)
                .jsonPath("$.amountRedeemed").isEqualTo(5000.00);

        assertSuccessfulState(scenario, "5000.00");
        Map<String, Object> coupon = jdbcTemplate.queryForMap(
                "select amount, status from coupons where id = ?", scenario.couponId());
        assertThat(coupon.get("amount").toString()).isEqualTo("7000.00");
        assertThat(coupon.get("status")).isEqualTo("USED");
    }

    @Test
    void hidesAnotherOwnersBookingAndLeavesCouponUnchanged() throws Exception {
        Scenario ownerA = scenario("owner-a@example.com", "5000.00", "5000.00", "PRIVATE-A");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");

        pay(ownerB.token(), ownerA.bookingId(), Map.of("couponCode", ownerA.couponCode()))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_FOUND");

        assertPendingAndUnconsumed(ownerA);
    }

    @Test
    void rejectsSecondPaymentForBooking() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "5000.00", "SECOND");
        pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", scenario.couponCode()))
                .expectStatus().isCreated();
        Long secondCouponId = issueCoupon(
                scenario.adminToken(), scenario.owner().id(), "6000.00", "SECOND-2").id();
        String secondCode = jdbcTemplate.queryForObject(
                "select code from coupons where id = ?", String.class, secondCouponId);

        pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", secondCode))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOKING_NOT_PENDING_PAYMENT");

        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select status from coupons where id = ?", String.class, secondCouponId)).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsUnknownOrAnotherOwnersCouponWithoutChangingState() throws Exception {
        Scenario ownerA = scenario("owner-a@example.com", "5000.00", "5000.00", "COUPON-A");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");
        IssuedCoupon ownerBCoupon = issueCoupon(
                ownerA.adminToken(), ownerB.id(), "5000.00", "COUPON-B");

        pay(ownerA.owner().token(), ownerA.bookingId(), Map.of("couponCode", "UNKNOWN-CODE"))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COUPON_NOT_FOUND");
        pay(ownerA.owner().token(), ownerA.bookingId(), Map.of("couponCode", ownerBCoupon.code()))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COUPON_NOT_FOUND");

        assertPendingAndUnconsumed(ownerA);
        assertThat(jdbcTemplate.queryForObject(
                "select status from coupons where id = ?", String.class, ownerBCoupon.id())).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsUsedCancelledExpiredAndElapsedCoupons() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "5000.00", "STATE");
        IssuedCoupon used = issueCoupon(
                scenario.adminToken(), scenario.owner().id(), "5000.00", "USED");
        IssuedCoupon cancelled = issueCoupon(
                scenario.adminToken(), scenario.owner().id(), "5000.00", "CANCELLED");
        IssuedCoupon expired = issueCoupon(
                scenario.adminToken(), scenario.owner().id(), "5000.00", "EXPIRED");
        jdbcTemplate.update("update coupons set status = 'USED', used_at = now() where id = ?", used.id());
        jdbcTemplate.update("update coupons set status = 'CANCELLED' where id = ?", cancelled.id());
        jdbcTemplate.update("update coupons set status = 'EXPIRED' where id = ?", expired.id());
        jdbcTemplate.update("""
                update coupons
                set created_at = now() - interval '2 days', expires_at = now() - interval '1 day'
                where id = ?
                """, scenario.couponId());

        for (String code : List.of(used.code(), cancelled.code(), expired.code(), scenario.couponCode())) {
            pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", code))
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("COUPON_NOT_REDEEMABLE");
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from coupon_redemptions", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select status from bookings where id = ?", String.class, scenario.bookingId()))
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void rejectsInsufficientCouponWithoutConsumption() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "4999.99", "LOW");

        pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", scenario.couponCode()))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("COUPON_INSUFFICIENT_VALUE");

        assertPendingAndUnconsumed(scenario);
    }

    @Test
    void ignoresForgedPaymentFields() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "7000.00", "FORGED");

        pay(scenario.owner().token(), scenario.bookingId(), """
                {
                  "couponCode": "%s",
                  "amount": 1.00,
                  "payerId": 999999,
                  "paymentMethod": "CASH",
                  "status": "FAILED",
                  "transactionReference": "CLIENT-FORGED",
                  "paidAt": "2020-01-01T00:00:00Z"
                }
                """.formatted(scenario.couponCode()))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.amount").isEqualTo(5000.00)
                .jsonPath("$.payerId").isEqualTo(scenario.owner().id().intValue())
                .jsonPath("$.paymentMethod").isEqualTo("COUPON")
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.transactionReference").value(value ->
                        assertThat(value.toString()).isNotEqualTo("CLIENT-FORGED"));
    }

    @Test
    void permitsExactlyOneOfTwoConcurrentIdenticalRequests() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "5000.00", "RACE-SAME");

        List<Integer> statuses = concurrentPayments(
                () -> paymentStatus(scenario.owner().token(), scenario.bookingId(), scenario.couponCode()),
                () -> paymentStatus(scenario.owner().token(), scenario.bookingId(), scenario.couponCode()));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertSuccessfulState(scenario, "5000.00");
    }

    @Test
    void permitsCouponForOnlyOneOfTwoConcurrentBookings() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "5000.00", "RACE-TWO");
        Long secondBookingId = createBooking(
                scenario.owner(), scenario.adminToken(), "RACE-TWO-SECOND", "5000.00");

        List<Integer> statuses = concurrentPayments(
                () -> paymentStatus(scenario.owner().token(), scenario.bookingId(), scenario.couponCode()),
                () -> paymentStatus(scenario.owner().token(), secondBookingId, scenario.couponCode()));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bookings where status = 'PENDING_APPROVAL'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bookings where status = 'PENDING_PAYMENT'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from coupon_redemptions", Long.class)).isEqualTo(1L);
    }

    @Test
    void databaseConstraintsRejectDuplicateSuccessAndRedemptionLinks() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "5000.00", "DB-DEFENSE");
        pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", scenario.couponCode()))
                .expectStatus().isCreated();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into payments
                            (booking_id, payer_id, amount, payment_method, status,
                             transaction_reference, paid_at, created_at)
                        values (?, ?, 5000.00, 'COUPON', 'SUCCESS', 'PAY-DUPLICATE', now(), now())
                        """, scenario.bookingId(), scenario.owner().id()))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long pendingPaymentId = jdbcTemplate.queryForObject("""
                insert into payments
                    (booking_id, payer_id, amount, payment_method, status,
                     transaction_reference, paid_at, created_at)
                values (?, ?, 5000.00, 'COUPON', 'PENDING', 'PAY-PENDING', null, now())
                returning id
                """, Long.class, scenario.bookingId(), scenario.owner().id());
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into coupon_redemptions
                            (coupon_id, payment_id, amount_redeemed, redeemed_at)
                        values (?, ?, 5000.00, now())
                        """, scenario.couponId(), pendingPaymentId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rollsBackEveryStateChangeWhenRedemptionPersistenceFails() throws Exception {
        Scenario scenario = scenario("owner@example.com", "5000.00", "5000.00", "ROLLBACK");
        jdbcTemplate.execute("""
                alter table coupon_redemptions
                add constraint ck_test_force_redemption_failure check (amount_redeemed < 0)
                """);
        try {
            pay(scenario.owner().token(), scenario.bookingId(), Map.of("couponCode", scenario.couponCode()))
                    .expectStatus().isEqualTo(409);
        } finally {
            jdbcTemplate.execute("""
                    alter table coupon_redemptions drop constraint ck_test_force_redemption_failure
                    """);
        }

        assertPendingAndUnconsumed(scenario);
    }

    private Scenario scenario(String email, String fee, String couponAmount, String suffix) throws Exception {
        Owner owner = registerAndLoginOwner(email);
        String adminToken = createAndLoginAdmin();
        Long bookingId = createBooking(owner, adminToken, suffix, fee);
        IssuedCoupon coupon = issueCoupon(adminToken, owner.id(), couponAmount, suffix);
        return new Scenario(owner, adminToken, bookingId, coupon.id(), coupon.code());
    }

    private Long createBooking(Owner owner, String adminToken, String suffix, String fee) throws Exception {
        Long vesselId = createApprovedVessel(owner, adminToken, "IMO-" + suffix);
        Long routeId = createRoute(adminToken, "RT-" + suffix, fee);
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
        return ((Number) json(body).get("id")).longValue();
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Payment Owner",
                        "email", email,
                        "password", OWNER_PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Long id = ((Number) json(body).get("id")).longValue();
        return new Owner(id, login(email, OWNER_PASSWORD));
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

    private Long createApprovedVessel(Owner owner, String adminToken, String registrationNumber)
            throws Exception {
        byte[] body = restTestClient.post()
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
        Long id = ((Number) json(body).get("id")).longValue();
        restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/approve", id)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk();
        return id;
    }

    private Long createRoute(String adminToken, String code, String fee) throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/routes")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "code", code,
                        "name", "Payment Route",
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

    private IssuedCoupon issueCoupon(String adminToken, Long ownerId, String amount, String suffix)
            throws Exception {
        byte[] body = restTestClient.post()
                .uri("/api/v1/admin/coupons")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "ownerId", ownerId,
                        "amount", amount,
                        "expiresAt", Instant.now().plusSeconds(86_400).toString(),
                        "label", suffix))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Map<String, Object> response = json(body);
        return new IssuedCoupon(
                ((Number) response.get("id")).longValue(), response.get("code").toString());
    }

    private RestTestClient.ResponseSpec pay(String token, Long bookingId, Object request) {
        return restTestClient.post()
                .uri("/api/v1/bookings/{id}/payments/coupon", bookingId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private int paymentStatus(String token, Long bookingId, String couponCode) {
        return pay(token, bookingId, Map.of("couponCode", couponCode))
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    private List<Integer> concurrentPayments(Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> synchronizedFirst = synchronizedCall(first, ready, start);
        Callable<Integer> synchronizedSecond = synchronizedCall(second, ready, start);
        try {
            Future<Integer> firstFuture = executor.submit(synchronizedFirst);
            Future<Integer> secondFuture = executor.submit(synchronizedSecond);
            ready.await();
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> synchronizedCall(
            Callable<Integer> request, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            return request.call();
        };
    }

    private void assertSuccessfulState(Scenario scenario, String redeemedAmount) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from coupon_redemptions", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select status from bookings where id = ?", String.class, scenario.bookingId()))
                .isEqualTo("PENDING_APPROVAL");
        Map<String, Object> coupon = jdbcTemplate.queryForMap(
                "select status, used_at from coupons where id = ?", scenario.couponId());
        assertThat(coupon.get("status")).isEqualTo("USED");
        assertThat(coupon.get("used_at")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select amount_redeemed from coupon_redemptions", String.class)).isEqualTo(redeemedAmount);
    }

    private void assertPendingAndUnconsumed(Scenario scenario) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from coupon_redemptions", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select status from bookings where id = ?", String.class, scenario.bookingId()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject(
                "select status from coupons where id = ?", String.class, scenario.couponId()))
                .isEqualTo("ACTIVE");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] responseBody) throws Exception {
        assertThat(responseBody).isNotNull();
        return objectMapper.readValue(new String(responseBody, StandardCharsets.UTF_8), Map.class);
    }

    private record Owner(Long id, String token) {
    }

    private record IssuedCoupon(Long id, String code) {
    }

    private record Scenario(
            Owner owner,
            String adminToken,
            Long bookingId,
            Long couponId,
            String couponCode) {
    }
}
