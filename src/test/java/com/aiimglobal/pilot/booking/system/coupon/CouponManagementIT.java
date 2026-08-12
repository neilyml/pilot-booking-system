package com.aiimglobal.pilot.booking.system.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

class CouponManagementIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";
    private static final String ADMIN_COUPONS_PATH = "/api/v1/admin/coupons";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void adminIssuesActiveCouponsWithUniqueServerCodesAndAuditOwnership() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        var admin = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);
        String expiresAt = Instant.now().plusSeconds(86_400).toString();

        byte[] firstBody = issueCoupon(adminToken, """
                {
                  "ownerId": %d,
                  "amount": 7500.00,
                  "expiresAt": "%s",
                  "code": "CLIENT-FORGED",
                  "status": "USED",
                  "issuedBy": %d
                }
                """.formatted(owner.id(), expiresAt, owner.id()))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/admin/coupons/[0-9]+")
                .expectBody()
                .jsonPath("$.code").value(value -> assertThat(value.toString()).matches("CPN-[0-9A-F-]{36}"))
                .jsonPath("$.ownerId").isEqualTo(owner.id().intValue())
                .jsonPath("$.ownerEmail").isEqualTo("owner@example.com")
                .jsonPath("$.amount").isEqualTo(7500.00)
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.expiresAt").isEqualTo(expiresAt)
                .jsonPath("$.issuedById").isEqualTo(admin.getId().intValue())
                .jsonPath("$.issuedByEmail").isEqualTo("admin@example.com")
                .jsonPath("$.createdAt").isNotEmpty()
                .jsonPath("$.usedAt").doesNotExist()
                .jsonPath("$.owner").doesNotExist()
                .jsonPath("$.issuedBy").doesNotExist()
                .jsonPath("$.version").doesNotExist()
                .returnResult()
                .getResponseBody();
        byte[] secondBody = issueCoupon(adminToken, validCoupon(owner.id(), "5000.00"))
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(json(firstBody).get("code")).isNotEqualTo(json(secondBody).get("code"));
        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "select * from coupons where code = ?", json(firstBody).get("code"));
        assertThat(((Number) persisted.get("owner_id")).longValue()).isEqualTo(owner.id());
        assertThat(((Number) persisted.get("issued_by")).longValue()).isEqualTo(admin.getId());
        assertThat(persisted.get("status")).isEqualTo("ACTIVE");
        assertThat(persisted.get("used_at")).isNull();
    }

    @Test
    void rejectsUnknownOwnerWithoutCreatingCoupon() throws Exception {
        String adminToken = createAndLoginAdmin();

        issueCoupon(adminToken, validCoupon(999_999L, "5000.00"))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COUPON_OWNER_NOT_FOUND");

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupons", Long.class)).isZero();
    }

    @Test
    void rejectsTargetWithoutOwnerRole() throws Exception {
        var nonOwner = testDataFactory.createAdmin("target-admin@example.com", ADMIN_PASSWORD);
        String issuingAdminToken = createAndLoginAdmin();

        issueCoupon(issuingAdminToken, validCoupon(nonOwner.getId(), "5000.00"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("COUPON_OWNER_NOT_ELIGIBLE");

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupons", Long.class)).isZero();
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();

        issueCoupon(adminToken, validCoupon(owner.id(), "0"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("amount");
        issueCoupon(adminToken, validCoupon(owner.id(), "-1"))
                .expectStatus().isBadRequest();

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupons", Long.class)).isZero();
    }

    @Test
    void rejectsExpiredOrCurrentExpiryWithoutCreatingCoupon() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        String adminToken = createAndLoginAdmin();

        issueCoupon(adminToken, Map.of(
                        "ownerId", owner.id(),
                        "amount", "5000.00",
                        "expiresAt", Instant.now().minusSeconds(60).toString()))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("expiresAt");

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupons", Long.class)).isZero();
    }

    @Test
    void ownerCannotIssueCoupons() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");

        issueCoupon(owner.token(), validCoupon(owner.id(), "5000.00"))
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from coupons", Long.class)).isZero();
    }

    @Test
    void ownerListsOnlyTheirCoupons() throws Exception {
        Owner ownerA = registerAndLoginOwner("owner-a@example.com");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");
        String adminToken = createAndLoginAdmin();
        String ownerACode = issuedCode(issueCoupon(adminToken, validCoupon(ownerA.id(), "5000.00")));
        issueCoupon(adminToken, validCoupon(ownerB.id(), "6000.00")).expectStatus().isCreated();
        String secondOwnerACode = issuedCode(issueCoupon(adminToken, validCoupon(ownerA.id(), "7000.00")));

        restTestClient.get()
                .uri("/api/v1/coupons")
                .headers(headers -> headers.setBearerAuth(ownerA.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].code").isEqualTo(secondOwnerACode)
                .jsonPath("$.content[0].ownerId").isEqualTo(ownerA.id().intValue())
                .jsonPath("$.content[1].code").isEqualTo(ownerACode)
                .jsonPath("$.content[1].ownerId").isEqualTo(ownerA.id().intValue())
                .jsonPath("$.content[2]").doesNotExist();
    }

    private String createAndLoginAdmin() throws Exception {
        testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        return login("admin@example.com", ADMIN_PASSWORD);
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        byte[] responseBody = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Coupon Owner",
                        "email", email,
                        "password", OWNER_PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Long ownerId = ((Number) json(responseBody).get("id")).longValue();
        return new Owner(ownerId, login(email, OWNER_PASSWORD));
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

    private RestTestClient.ResponseSpec issueCoupon(String token, Object request) {
        return restTestClient.post()
                .uri(ADMIN_COUPONS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private String issuedCode(RestTestClient.ResponseSpec response) throws Exception {
        byte[] responseBody = response
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return json(responseBody).get("code").toString();
    }

    private Map<String, Object> validCoupon(Long ownerId, String amount) {
        return Map.of(
                "ownerId", ownerId,
                "amount", amount,
                "expiresAt", Instant.now().plusSeconds(86_400).toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] responseBody) throws Exception {
        assertThat(responseBody).isNotNull();
        return objectMapper.readValue(new String(responseBody, StandardCharsets.UTF_8), Map.class);
    }

    private record Owner(Long id, String token) {
    }
}
