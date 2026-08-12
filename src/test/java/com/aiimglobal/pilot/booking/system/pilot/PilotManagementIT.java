package com.aiimglobal.pilot.booking.system.pilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

class PilotManagementIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";
    private static final String PILOTS_PATH = "/api/v1/admin/pilots";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void createsActiveMaritimePilotWithServerControlledLifecycle() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");

        createPilot(adminToken, """
                {
                  "employeeNumber": " mp-1001 ",
                  "name": " Captain Amina Rahman ",
                  "phone": "+8801700000001",
                  "email": "amina@example.com",
                  "status": "INACTIVE",
                  "version": 999
                }
                """)
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/admin/pilots/[0-9]+")
                .expectBody()
                .jsonPath("$.employeeNumber").isEqualTo("MP-1001")
                .jsonPath("$.name").isEqualTo("Captain Amina Rahman")
                .jsonPath("$.phone").isEqualTo("+8801700000001")
                .jsonPath("$.email").isEqualTo("amina@example.com")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.version").isEqualTo(0)
                .jsonPath("$.createdAt").isNotEmpty()
                .jsonPath("$.updatedAt").isNotEmpty();

        Map<String, Object> pilot = jdbcTemplate.queryForMap("select * from pilots");
        assertThat(pilot.get("employee_number")).isEqualTo("MP-1001");
        assertThat(pilot.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void listsPilotRosterInStableOrder() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        createPilot(adminToken, validPilot("MP-2001", "First Pilot")).expectStatus().isCreated();
        createPilot(adminToken, validPilot("MP-2002", "Second Pilot")).expectStatus().isCreated();

        restTestClient.get()
                .uri(PILOTS_PATH)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].employeeNumber").isEqualTo("MP-2002")
                .jsonPath("$.content[1].employeeNumber").isEqualTo("MP-2001")
                .jsonPath("$.content[2]").doesNotExist();
    }

    @Test
    void rejectsDuplicateEmployeeNumberOnCreateAndUpdate() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        Long firstId = createdPilotId(adminToken, validPilot("MP-DUP", "First Pilot"));
        Long secondId = createdPilotId(adminToken, validPilot("MP-OTHER", "Second Pilot"));

        createPilot(adminToken, validPilot("mp-dup", "Duplicate Pilot"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PILOT_EMPLOYEE_NUMBER_EXISTS");
        updatePilot(adminToken, secondId, updateRequest("MP-DUP", "Second Pilot", 0))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PILOT_EMPLOYEE_NUMBER_EXISTS");

        assertThat(jdbcTemplate.queryForObject("select count(*) from pilots", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "select employee_number from pilots where id = ?", String.class, firstId)).isEqualTo("MP-DUP");
    }

    @Test
    void updatesPermittedPilotProfileFields() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        Long pilotId = createdPilotId(adminToken, validPilot("MP-UPDATE", "Original Name"));

        updatePilot(adminToken, pilotId, Map.of(
                        "employeeNumber", "MP-UPDATED",
                        "name", "Updated Name",
                        "phone", "+8801700000999",
                        "email", "updated@example.com",
                        "version", 0))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.employeeNumber").isEqualTo("MP-UPDATED")
                .jsonPath("$.name").isEqualTo("Updated Name")
                .jsonPath("$.phone").isEqualTo("+8801700000999")
                .jsonPath("$.email").isEqualTo("updated@example.com")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.version").isEqualTo(1);
    }

    @Test
    void profileUpdateCannotMutateLifecycleStatus() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        Long pilotId = createdPilotId(adminToken, validPilot("MP-LIFECYCLE", "Lifecycle Pilot"));

        updatePilot(adminToken, pilotId, """
                {
                  "employeeNumber": "MP-LIFECYCLE",
                  "name": "Lifecycle Pilot",
                  "phone": "+8801700000000",
                  "email": "pilot@example.com",
                  "version": 0,
                  "status": "INACTIVE"
                }
                """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE");

        assertThat(pilotRow(pilotId).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void deactivatesIdleActivePilot() throws Exception {
        String adminToken = createAndLoginAdmin("admin@example.com");
        Long pilotId = createdPilotId(adminToken, validPilot("MP-IDLE", "Idle Pilot"));

        restTestClient.post()
                .uri(PILOTS_PATH + "/{id}/deactivate", pilotId)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("INACTIVE")
                .jsonPath("$.version").isEqualTo(1);

        assertThat(pilotRow(pilotId).get("status")).isEqualTo("INACTIVE");
    }

    @Test
    void ownerCannotManagePilotRoster() throws Exception {
        String ownerToken = registerAndLoginOwner("owner@example.com");

        createPilot(ownerToken, validPilot("MP-FORBIDDEN", "Forbidden Pilot"))
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from pilots", Long.class)).isZero();
    }

    @Test
    void rejectsStaleVersionWithoutOverwritingNewerProfile() throws Exception {
        String firstAdmin = createAndLoginAdmin("admin-one@example.com");
        String secondAdmin = createAndLoginAdmin("admin-two@example.com");
        Long pilotId = createdPilotId(firstAdmin, validPilot("MP-STALE", "Original Name"));

        updatePilot(firstAdmin, pilotId, updateRequest("MP-STALE", "First Admin Update", 0))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.version").isEqualTo(1);
        updatePilot(secondAdmin, pilotId, updateRequest("MP-STALE", "Stale Overwrite", 0))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PILOT_STALE_VERSION");

        Map<String, Object> pilot = pilotRow(pilotId);
        assertThat(pilot.get("name")).isEqualTo("First Admin Update");
        assertThat(((Number) pilot.get("version")).longValue()).isEqualTo(1L);
    }

    private String createAndLoginAdmin(String email) throws Exception {
        testDataFactory.createAdmin(email, ADMIN_PASSWORD);
        return login(email, ADMIN_PASSWORD);
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

    private RestTestClient.ResponseSpec createPilot(String token, Object request) {
        return restTestClient.post()
                .uri(PILOTS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private Long createdPilotId(String token, Object request) throws Exception {
        byte[] body = createPilot(token, request)
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(body).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec updatePilot(String token, Long pilotId, Object request) {
        return restTestClient.put()
                .uri(PILOTS_PATH + "/{id}", pilotId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private Map<String, Object> validPilot(String employeeNumber, String name) {
        return Map.of(
                "employeeNumber", employeeNumber,
                "name", name,
                "phone", "+8801700000000",
                "email", "pilot@example.com");
    }

    private Map<String, Object> updateRequest(
            String employeeNumber, String name, long version) {
        return Map.of(
                "employeeNumber", employeeNumber,
                "name", name,
                "phone", "+8801700000000",
                "email", "pilot@example.com",
                "version", version);
    }

    private Map<String, Object> pilotRow(Long pilotId) {
        return jdbcTemplate.queryForMap("select * from pilots where id = ?", pilotId);
    }
}
