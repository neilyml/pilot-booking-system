package com.aiimglobal.pilot.booking.system.vessel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

class OwnerVesselIT extends IntegrationTestBase {

    private static final String OWNER_PASSWORD = "owner-password";
    private static final String VESSELS_PATH = "/api/v1/vessels";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void registersPendingVesselForAuthenticatedOwner() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");

        createVessel(owner.token(), validVessel("MV Horizon", "IMO-1001"))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/vessels/[0-9]+")
                .expectBody()
                .jsonPath("$.ownerId").isEqualTo(owner.id().intValue())
                .jsonPath("$.name").isEqualTo("MV Horizon")
                .jsonPath("$.registrationNumber").isEqualTo("IMO-1001")
                .jsonPath("$.vesselType").isEqualTo("Container Ship")
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.createdAt").isNotEmpty()
                .jsonPath("$.owner").doesNotExist()
                .jsonPath("$.reviewedBy").doesNotExist()
                .jsonPath("$.version").doesNotExist();

        Map<String, Object> vessel = jdbcTemplate.queryForMap("select * from vessels");
        assertThat(((Number) vessel.get("owner_id")).longValue()).isEqualTo(owner.id());
        assertThat(vessel.get("status")).isEqualTo("PENDING");
        assertThat(vessel.get("reviewed_by")).isNull();
        assertThat(vessel.get("reviewed_at")).isNull();
        assertThat(vessel.get("rejection_reason")).isNull();
    }

    @Test
    void rejectsDuplicateRegistrationNumber() throws Exception {
        Owner firstOwner = registerAndLoginOwner("first@example.com");
        Owner secondOwner = registerAndLoginOwner("second@example.com");
        createVessel(firstOwner.token(), validVessel("MV First", "IMO-DUPLICATE"))
                .expectStatus().isCreated();

        createVessel(secondOwner.token(), validVessel("MV Second", "IMO-DUPLICATE"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VESSEL_REGISTRATION_EXISTS");

        assertThat(jdbcTemplate.queryForObject("select count(*) from vessels", Long.class)).isEqualTo(1L);
    }

    @Test
    void reportsFieldErrorsAndPersistsNothingForInvalidVessel() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");

        createVessel(owner.token(), Map.of(
                        "name", " ",
                        "registrationNumber", " ",
                        "vesselType", " "))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("name")
                .jsonPath("$.fieldErrors[1].field").isEqualTo("registrationNumber")
                .jsonPath("$.fieldErrors[2].field").isEqualTo("vesselType");

        assertThat(jdbcTemplate.queryForObject("select count(*) from vessels", Long.class)).isZero();
    }

    @Test
    void ignoresForgedOwnerAndStatusFields() throws Exception {
        Owner authenticatedOwner = registerAndLoginOwner("owner@example.com");
        Owner anotherOwner = registerAndLoginOwner("another@example.com");

        createVessel(authenticatedOwner.token(), """
                {
                  "name": "MV Authoritative",
                  "registrationNumber": "IMO-1002",
                  "vesselType": "Tanker",
                  "ownerId": %d,
                  "status": "APPROVED",
                  "reviewedBy": %d,
                  "rejectionReason": "forged"
                }
                """.formatted(anotherOwner.id(), anotherOwner.id()))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.ownerId").isEqualTo(authenticatedOwner.id().intValue())
                .jsonPath("$.status").isEqualTo("PENDING");

        Map<String, Object> vessel = jdbcTemplate.queryForMap("select * from vessels");
        assertThat(((Number) vessel.get("owner_id")).longValue()).isEqualTo(authenticatedOwner.id());
        assertThat(vessel.get("status")).isEqualTo("PENDING");
        assertThat(vessel.get("reviewed_by")).isNull();
        assertThat(vessel.get("rejection_reason")).isNull();
    }

    @Test
    void listsOnlyAuthenticatedOwnersVessels() throws Exception {
        Owner ownerA = registerAndLoginOwner("owner-a@example.com");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");
        createVessel(ownerA.token(), validVessel("MV A One", "IMO-A-1")).expectStatus().isCreated();
        createVessel(ownerB.token(), validVessel("MV B One", "IMO-B-1")).expectStatus().isCreated();
        createVessel(ownerA.token(), validVessel("MV A Two", "IMO-A-2")).expectStatus().isCreated();

        restTestClient.get()
                .uri(VESSELS_PATH)
                .headers(headers -> headers.setBearerAuth(ownerA.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].registrationNumber").isEqualTo("IMO-A-2")
                .jsonPath("$.content[1].registrationNumber").isEqualTo("IMO-A-1")
                .jsonPath("$.content[2]").doesNotExist();
    }

    @Test
    void returnsOwnedVesselDetail() throws Exception {
        Owner owner = registerAndLoginOwner("owner@example.com");
        Long vesselId = createVesselAndGetId(owner.token(), validVessel("MV Detail", "IMO-DETAIL"));

        restTestClient.get()
                .uri(VESSELS_PATH + "/{id}", vesselId)
                .headers(headers -> headers.setBearerAuth(owner.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(vesselId.intValue())
                .jsonPath("$.ownerId").isEqualTo(owner.id().intValue())
                .jsonPath("$.registrationNumber").isEqualTo("IMO-DETAIL");
    }

    @Test
    void hidesAnotherOwnersVesselAsNotFound() throws Exception {
        Owner ownerA = registerAndLoginOwner("owner-a@example.com");
        Owner ownerB = registerAndLoginOwner("owner-b@example.com");
        Long ownerBVesselId = createVesselAndGetId(ownerB.token(), validVessel("MV Private", "IMO-PRIVATE"));

        restTestClient.get()
                .uri(VESSELS_PATH + "/{id}", ownerBVesselId)
                .headers(headers -> headers.setBearerAuth(ownerA.token()))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VESSEL_NOT_FOUND");
    }

    @Test
    void adminWithoutOwnerRoleCannotCreateVessel() throws Exception {
        testDataFactory.createAdmin("admin@example.com", "admin-password");
        String adminToken = login("admin@example.com", "admin-password");

        createVessel(adminToken, validVessel("MV Forbidden", "IMO-FORBIDDEN"))
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from vessels", Long.class)).isZero();
    }

    private Owner registerAndLoginOwner(String email) throws Exception {
        byte[] responseBody = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Vessel Owner",
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

    private RestTestClient.ResponseSpec createVessel(String token, Object request) {
        return restTestClient.post()
                .uri(VESSELS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private Long createVesselAndGetId(String token, Object request) throws Exception {
        byte[] responseBody = createVessel(token, request)
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(responseBody).get("id")).longValue();
    }

    private Map<String, String> validVessel(String name, String registrationNumber) {
        return Map.of(
                "name", name,
                "registrationNumber", registrationNumber,
                "vesselType", "Container Ship");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] responseBody) throws Exception {
        assertThat(responseBody).isNotNull();
        return objectMapper.readValue(new String(responseBody, StandardCharsets.UTF_8), Map.class);
    }

    private record Owner(Long id, String token) {
    }
}
