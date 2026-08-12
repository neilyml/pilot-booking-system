package com.aiimglobal.pilot.booking.system.vessel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

class AdminVesselReviewIT extends IntegrationTestBase {

    private static final String OWNER_PASSWORD = "owner-password";
    private static final String ADMIN_PASSWORD = "admin-password";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void listsPendingVesselsAcrossOwnersWithOperationalDetails() throws Exception {
        String ownerAToken = registerAndLoginOwner("owner-a@example.com");
        String ownerBToken = registerAndLoginOwner("owner-b@example.com");
        Long firstId = createVessel(ownerAToken, "MV First", "IMO-QUEUE-1");
        Long secondId = createVessel(ownerBToken, "MV Second", "IMO-QUEUE-2");
        Long excludedId = createVessel(ownerBToken, "MV Approved", "IMO-QUEUE-3");
        String adminToken = createAndLoginAdmin("admin@example.com");
        approve(excludedId, adminToken).expectStatus().isOk();

        restTestClient.get()
                .uri("/api/v1/admin/vessels?status=PENDING")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].id").isEqualTo(secondId.intValue())
                .jsonPath("$.content[0].ownerEmail").isEqualTo("owner-b@example.com")
                .jsonPath("$.content[0].status").isEqualTo("PENDING")
                .jsonPath("$.content[1].id").isEqualTo(firstId.intValue())
                .jsonPath("$.content[1].ownerEmail").isEqualTo("owner-a@example.com")
                .jsonPath("$.content[1].status").isEqualTo("PENDING")
                .jsonPath("$.content[2]").doesNotExist();
    }

    @Test
    void approvesPendingVesselAndRecordsReviewerAndTime() throws Exception {
        String ownerToken = registerAndLoginOwner("owner@example.com");
        Long vesselId = createVessel(ownerToken, "MV Approve", "IMO-APPROVE");
        Long adminId = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD).getId();
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);

        restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/approve", vesselId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.reviewedById").isEqualTo(adminId.intValue())
                .jsonPath("$.reviewedByEmail").isEqualTo("admin@example.com")
                .jsonPath("$.reviewedAt").isNotEmpty()
                .jsonPath("$.rejectionReason").doesNotExist();

        Map<String, Object> vessel = vesselRow(vesselId);
        assertThat(vessel.get("status")).isEqualTo("APPROVED");
        assertThat(((Number) vessel.get("reviewed_by")).longValue()).isEqualTo(adminId);
        assertThat(vessel.get("reviewed_at")).isNotNull();
        assertThat(vessel.get("rejection_reason")).isNull();
    }

    @Test
    void rejectsPendingVesselWithReasonReviewerAndTime() throws Exception {
        String ownerToken = registerAndLoginOwner("owner@example.com");
        Long vesselId = createVessel(ownerToken, "MV Reject", "IMO-REJECT");
        Long adminId = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD).getId();
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);

        restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/reject", vesselId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of("reason", "Registration documents are incomplete."))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.reviewedById").isEqualTo(adminId.intValue())
                .jsonPath("$.reviewedAt").isNotEmpty()
                .jsonPath("$.rejectionReason").isEqualTo("Registration documents are incomplete.");

        Map<String, Object> vessel = vesselRow(vesselId);
        assertThat(vessel.get("status")).isEqualTo("REJECTED");
        assertThat(((Number) vessel.get("reviewed_by")).longValue()).isEqualTo(adminId);
        assertThat(vessel.get("reviewed_at")).isNotNull();
        assertThat(vessel.get("rejection_reason")).isEqualTo("Registration documents are incomplete.");
    }

    @Test
    void rejectsBlankReasonWithoutChangingPendingVessel() throws Exception {
        String ownerToken = registerAndLoginOwner("owner@example.com");
        Long vesselId = createVessel(ownerToken, "MV Pending", "IMO-PENDING");
        String adminToken = createAndLoginAdmin("admin@example.com");

        restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/reject", vesselId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .contentType(APPLICATION_JSON)
                .body(Map.of("reason", " "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("reason");

        Map<String, Object> vessel = vesselRow(vesselId);
        assertThat(vessel.get("status")).isEqualTo("PENDING");
        assertThat(vessel.get("reviewed_by")).isNull();
        assertThat(vessel.get("reviewed_at")).isNull();
    }

    @Test
    void rejectsASecondFinalDecision() throws Exception {
        String ownerToken = registerAndLoginOwner("owner@example.com");
        Long vesselId = createVessel(ownerToken, "MV Final", "IMO-FINAL");
        String adminToken = createAndLoginAdmin("admin@example.com");
        approve(vesselId, adminToken).expectStatus().isOk();

        reject(vesselId, adminToken, "Changed decision")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VESSEL_ALREADY_REVIEWED");

        assertThat(vesselRow(vesselId).get("status")).isEqualTo("APPROVED");
    }

    @Test
    void ownerCannotReviewVessels() throws Exception {
        String ownerToken = registerAndLoginOwner("owner@example.com");
        Long vesselId = createVessel(ownerToken, "MV Protected", "IMO-PROTECTED");

        approve(vesselId, ownerToken)
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        assertThat(vesselRow(vesselId).get("status")).isEqualTo("PENDING");
    }

    @Test
    void concurrentApproveAndRejectAllowExactlyOneFinalDecision() throws Exception {
        String ownerToken = registerAndLoginOwner("owner@example.com");
        Long vesselId = createVessel(ownerToken, "MV Concurrent", "IMO-CONCURRENT");
        String firstAdminToken = createAndLoginAdmin("admin-one@example.com");
        String secondAdminToken = createAndLoginAdmin("admin-two@example.com");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        List<EntityExchangeResult<byte[]>> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approve = executor.submit(() -> {
                ready.countDown();
                start.await();
                return approve(vesselId, firstAdminToken).expectBody().returnResult();
            });
            var reject = executor.submit(() -> {
                ready.countDown();
                start.await();
                return reject(vesselId, secondAdminToken, "Concurrent rejection")
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
                .isIn("VESSEL_REVIEW_CONFLICT", "VESSEL_ALREADY_REVIEWED");
        Map<String, Object> vessel = vesselRow(vesselId);
        assertThat(vessel.get("status")).isEqualTo(winningStatus);
        assertThat(vessel.get("reviewed_by")).isNotNull();
        assertThat(vessel.get("reviewed_at")).isNotNull();
        if ("APPROVED".equals(winningStatus)) {
            assertThat(vessel.get("rejection_reason")).isNull();
        } else {
            assertThat(vessel.get("rejection_reason")).isEqualTo("Concurrent rejection");
        }
    }

    private String registerAndLoginOwner(String email) throws Exception {
        restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Vessel Owner",
                        "email", email,
                        "password", OWNER_PASSWORD))
                .exchange()
                .expectStatus().isCreated();
        return login(email, OWNER_PASSWORD);
    }

    private String createAndLoginAdmin(String email) throws Exception {
        testDataFactory.createAdmin(email, ADMIN_PASSWORD);
        return login(email, ADMIN_PASSWORD);
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

    private Long createVessel(String token, String name, String registrationNumber) throws Exception {
        byte[] responseBody = restTestClient.post()
                .uri("/api/v1/vessels")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "name", name,
                        "registrationNumber", registrationNumber,
                        "vesselType", "Container Ship"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(responseBody).get("id")).longValue();
    }

    private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec approve(
            Long vesselId, String token) {
        return restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/approve", vesselId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec reject(
            Long vesselId, String token, String reason) {
        return restTestClient.post()
                .uri("/api/v1/admin/vessels/{id}/reject", vesselId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(Map.of("reason", reason))
                .exchange();
    }

    private Map<String, Object> vesselRow(Long vesselId) {
        return jdbcTemplate.queryForMap("select * from vessels where id = ?", vesselId);
    }
}
