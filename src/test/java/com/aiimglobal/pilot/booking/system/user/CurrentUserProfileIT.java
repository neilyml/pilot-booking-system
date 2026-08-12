package com.aiimglobal.pilot.booking.system.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

class CurrentUserProfileIT extends IntegrationTestBase {

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void returnsOwnerProfileFromAuthenticatedIdentityAndExposesOnlySafeFields() throws Exception {
        Long ownerId = registerOwner();
        Long anotherUserId = testDataFactory.createAdmin("admin@example.com", "admin-password").getId();
        String accessToken = login("owner@example.com", "owner-password");

        byte[] responseBody = restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/me")
                        .queryParam("userId", anotherUserId)
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(ownerId.intValue())
                .jsonPath("$.email").isEqualTo("owner@example.com")
                .jsonPath("$.phone").isEqualTo("+8801700000000")
                .jsonPath("$.fullName").isEqualTo("Profile Owner")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.roles[0]").isEqualTo("OWNER")
                .jsonPath("$.createdAt").isNotEmpty()
                .returnResult()
                .getResponseBody();

        assertThat(json(responseBody).keySet())
                .containsExactlyInAnyOrder("id", "email", "phone", "fullName", "status", "roles", "createdAt");
    }

    @Test
    void returnsAdminProfileWithAdminRole() throws Exception {
        var admin = testDataFactory.createAdmin("admin@example.com", "admin-password");
        String accessToken = login("admin@example.com", "admin-password");

        byte[] responseBody = restTestClient.get()
                .uri("/api/v1/me")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(admin.getId().intValue())
                .jsonPath("$.email").isEqualTo("admin@example.com")
                .jsonPath("$.phone").doesNotExist()
                .jsonPath("$.fullName").isEqualTo("System Administrator")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.roles[0]").isEqualTo("ADMIN")
                .jsonPath("$.createdAt").isNotEmpty()
                .returnResult()
                .getResponseBody();

        assertThat(json(responseBody).keySet())
                .containsExactlyInAnyOrder("id", "email", "fullName", "status", "roles", "createdAt");
    }

    private Long registerOwner() throws Exception {
        byte[] responseBody = restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Profile Owner",
                        "email", "owner@example.com",
                        "phone", "+8801700000000",
                        "password", "owner-password"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return ((Number) json(responseBody).get("id")).longValue();
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
}
