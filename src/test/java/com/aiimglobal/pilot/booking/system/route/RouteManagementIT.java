package com.aiimglobal.pilot.booking.system.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

class RouteManagementIT extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String OWNER_PASSWORD = "owner-password";
    private static final String ADMIN_ROUTES_PATH = "/api/v1/admin/routes";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void adminCreatesActiveRouteAndIsRecordedAsCreator() throws Exception {
        var admin = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);

        createRoute(adminToken, validRoute("RT-001", "5000.00"))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/admin/routes/[0-9]+")
                .expectBody()
                .jsonPath("$.code").isEqualTo("RT-001")
                .jsonPath("$.name").isEqualTo("Outer Anchorage to Port")
                .jsonPath("$.origin").isEqualTo("Outer Anchorage")
                .jsonPath("$.destination").isEqualTo("Main Port")
                .jsonPath("$.serviceFee").isEqualTo(5000.00)
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.createdById").isEqualTo(admin.getId().intValue())
                .jsonPath("$.createdByEmail").isEqualTo("admin@example.com")
                .jsonPath("$.createdAt").isNotEmpty()
                .jsonPath("$.updatedAt").isNotEmpty()
                .jsonPath("$.createdBy").doesNotExist()
                .jsonPath("$.version").doesNotExist();

        Map<String, Object> route = jdbcTemplate.queryForMap("select * from routes");
        assertThat(route.get("active")).isEqualTo(true);
        assertThat(((Number) route.get("created_by")).longValue()).isEqualTo(admin.getId());
    }

    @Test
    void updatesRouteWhileRetainingItsOwnCodeAndCreator() throws Exception {
        var admin = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);
        Long routeId = createRouteAndGetId(adminToken, validRoute("RT-001", "5000.00"));

        updateRoute(adminToken, routeId, Map.of(
                        "code", "RT-001",
                        "name", "Updated Pilotage Route",
                        "origin", "Updated Origin",
                        "destination", "Updated Destination",
                        "serviceFee", "7250.50"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(routeId.intValue())
                .jsonPath("$.code").isEqualTo("RT-001")
                .jsonPath("$.name").isEqualTo("Updated Pilotage Route")
                .jsonPath("$.serviceFee").isEqualTo(7250.50)
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.createdById").isEqualTo(admin.getId().intValue());

        assertThat(jdbcTemplate.queryForObject("select count(*) from routes", Long.class)).isEqualTo(1L);
    }

    @Test
    void rejectsCodeCollisionOnCreateAndUpdate() throws Exception {
        String adminToken = createAndLoginAdmin();
        Long firstId = createRouteAndGetId(adminToken, validRoute("RT-001", "5000.00"));
        Long secondId = createRouteAndGetId(adminToken, validRoute("RT-002", "6000.00"));

        createRoute(adminToken, validRoute("RT-001", "7000.00"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("ROUTE_CODE_EXISTS");

        updateRoute(adminToken, secondId, validRoute("RT-001", "8000.00"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("ROUTE_CODE_EXISTS");

        assertThat(jdbcTemplate.queryForObject("select code from routes where id = ?", String.class, firstId))
                .isEqualTo("RT-001");
        assertThat(jdbcTemplate.queryForObject("select code from routes where id = ?", String.class, secondId))
                .isEqualTo("RT-002");
    }

    @Test
    void rejectsNonPositiveFeesAtApiAndDatabaseBoundaries() throws Exception {
        var admin = testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        String adminToken = login("admin@example.com", ADMIN_PASSWORD);
        Long routeId = createRouteAndGetId(adminToken, validRoute("RT-VALID", "5000.00"));

        createRoute(adminToken, validRoute("RT-ZERO", "0"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("serviceFee");
        updateRoute(adminToken, routeId, validRoute("RT-VALID", "-1"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("serviceFee");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into routes (
                    code, name, origin, destination, service_fee, active,
                    created_by, created_at, updated_at, version
                ) values (?, ?, ?, ?, ?, true, ?, current_timestamp, current_timestamp, 0)
                """, "RT-DB-INVALID", "Invalid Route", "A", "B", BigDecimal.ZERO, admin.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select service_fee from routes where id = ?", BigDecimal.class, routeId))
                .isEqualByComparingTo("5000.00");
    }

    @Test
    void deactivatesAndReactivatesRouteWithoutDeletingIt() throws Exception {
        String adminToken = createAndLoginAdmin();
        Long routeId = createRouteAndGetId(adminToken, validRoute("RT-001", "5000.00"));

        changeActivation(adminToken, routeId, "deactivate")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);
        assertThat(jdbcTemplate.queryForObject("select count(*) from routes where id = ?", Long.class, routeId))
                .isEqualTo(1L);

        changeActivation(adminToken, routeId, "activate")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true);
    }

    @Test
    void ownerListsOnlyActiveRoutes() throws Exception {
        String adminToken = createAndLoginAdmin();
        Long activeId = createRouteAndGetId(adminToken, validRoute("RT-ACTIVE", "5000.00"));
        Long inactiveId = createRouteAndGetId(adminToken, validRoute("RT-INACTIVE", "6000.00"));
        changeActivation(adminToken, inactiveId, "deactivate").expectStatus().isOk();
        String ownerToken = registerAndLoginOwner();

        restTestClient.get()
                .uri("/api/v1/routes")
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(activeId.intValue())
                .jsonPath("$[0].code").isEqualTo("RT-ACTIVE")
                .jsonPath("$[0].active").isEqualTo(true)
                .jsonPath("$[1]").doesNotExist();
    }

    @Test
    void inactiveRouteIsHiddenFromOwnerDetailUntilReactivated() throws Exception {
        String adminToken = createAndLoginAdmin();
        Long routeId = createRouteAndGetId(adminToken, validRoute("RT-001", "5000.00"));
        changeActivation(adminToken, routeId, "deactivate").expectStatus().isOk();
        String ownerToken = registerAndLoginOwner();

        ownerRouteDetail(ownerToken, routeId)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ROUTE_NOT_FOUND");

        changeActivation(adminToken, routeId, "activate").expectStatus().isOk();
        ownerRouteDetail(ownerToken, routeId)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(routeId.intValue())
                .jsonPath("$.active").isEqualTo(true);
    }

    @Test
    void ownerCannotManageRoutes() throws Exception {
        String ownerToken = registerAndLoginOwner();

        createRoute(ownerToken, validRoute("RT-FORBIDDEN", "5000.00"))
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from routes", Long.class)).isZero();
    }

    private String createAndLoginAdmin() throws Exception {
        testDataFactory.createAdmin("admin@example.com", ADMIN_PASSWORD);
        return login("admin@example.com", ADMIN_PASSWORD);
    }

    private String registerAndLoginOwner() throws Exception {
        restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Route Browser",
                        "email", "owner@example.com",
                        "password", OWNER_PASSWORD))
                .exchange()
                .expectStatus().isCreated();
        return login("owner@example.com", OWNER_PASSWORD);
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

    private RestTestClient.ResponseSpec createRoute(String token, Object request) {
        return restTestClient.post()
                .uri(ADMIN_ROUTES_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private Long createRouteAndGetId(String token, Object request) throws Exception {
        byte[] responseBody = createRoute(token, request)
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(responseBody).get("id")).longValue();
    }

    private RestTestClient.ResponseSpec updateRoute(String token, Long routeId, Object request) {
        return restTestClient.put()
                .uri(ADMIN_ROUTES_PATH + "/{id}", routeId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private RestTestClient.ResponseSpec changeActivation(String token, Long routeId, String operation) {
        return restTestClient.post()
                .uri(ADMIN_ROUTES_PATH + "/{id}/" + operation, routeId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private RestTestClient.ResponseSpec ownerRouteDetail(String token, Long routeId) {
        return restTestClient.get()
                .uri("/api/v1/routes/{id}", routeId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();
    }

    private Map<String, String> validRoute(String code, String serviceFee) {
        return Map.of(
                "code", code,
                "name", "Outer Anchorage to Port",
                "origin", "Outer Anchorage",
                "destination", "Main Port",
                "serviceFee", serviceFee);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] responseBody) throws Exception {
        assertThat(responseBody).isNotNull();
        return objectMapper.readValue(new String(responseBody, StandardCharsets.UTF_8), Map.class);
    }
}
