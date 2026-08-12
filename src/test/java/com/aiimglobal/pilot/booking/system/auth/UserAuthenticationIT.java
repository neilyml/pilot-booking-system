package com.aiimglobal.pilot.booking.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.support.TestDataFactory;

import tools.jackson.databind.ObjectMapper;

@Import(UserAuthenticationIT.SecurityProbeController.class)
class UserAuthenticationIT extends IntegrationTestBase {

    private static final String ISSUER = "https://pilot-booking-system.local";

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void authenticatesOwnerAndIssuesUsableBearerTokenWithExpiryMetadata() throws Exception {
        registerOwner("owner@example.com", "strong-password");

        var result = login("  OWNER@EXAMPLE.COM ", "strong-password")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isNumber()
                .jsonPath("$.expiresAt").isNotEmpty()
                .returnResult();
        String token = json(result.getResponseBody()).get("accessToken").toString();

        restTestClient.get()
                .uri("/api/v1/test/protected")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void authenticatesAdminWithAdminAuthority() throws Exception {
        testDataFactory.createAdmin("admin@example.com", "admin-password");

        String token = accessToken(login("admin@example.com", "admin-password")
                .expectStatus().isOk()
                .expectBody()
                .returnResult().getResponseBody());

        assertThat(jwtDecoder.decode(token).getClaimAsStringList("roles")).containsExactly("ADMIN");
        restTestClient.get()
                .uri("/api/v1/admin/test")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void tokenContainsRequiredStandardAndRoleClaims() throws Exception {
        registerOwner("claims@example.com", "strong-password");

        String token = accessToken(login("claims@example.com", "strong-password")
                .expectStatus().isOk()
                .expectBody()
                .returnResult().getResponseBody());
        var jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("claims@example.com");
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("OWNER");
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameGenericUnauthorizedContract() throws Exception {
        registerOwner("owner@example.com", "strong-password");

        var wrongPassword = login("owner@example.com", "wrong-password")
                .expectStatus().isUnauthorized()
                .expectBody()
                .returnResult();
        var unknownEmail = login("unknown@example.com", "wrong-password")
                .expectStatus().isUnauthorized()
                .expectBody()
                .returnResult();

        assertThat(json(wrongPassword.getResponseBody()).get("code").toString())
                .isEqualTo("INVALID_CREDENTIALS")
                .isEqualTo(json(unknownEmail.getResponseBody()).get("code").toString());
        assertThat(json(wrongPassword.getResponseBody()).get("message").toString())
                .isEqualTo(json(unknownEmail.getResponseBody()).get("message").toString());
    }

    @Test
    void disabledUserCannotReceiveToken() {
        createUser("disabled@example.com", "strong-password", "DISABLED", "OWNER");

        login("disabled@example.com", "strong-password")
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS")
                .jsonPath("$.accessToken").doesNotExist();
    }

    @Test
    void missingTokenReturnsJsonUnauthorizedBeforeControllerRuns() {
        restTestClient.get()
                .uri("/api/v1/test/protected")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.path").isEqualTo("/api/v1/test/protected");
    }

    @Test
    void malformedExpiredAndForgedTokensAreRejectedBeforeControllerRuns() throws Exception {
        assertUnauthorizedToken("not-a-jwt");
        assertUnauthorizedToken(expiredToken());
        assertUnauthorizedToken(forgedToken());
    }

    @Test
    void ownerTokenCannotAccessAdminEndpoint() throws Exception {
        registerOwner("owner@example.com", "strong-password");
        String token = accessToken(login("owner@example.com", "strong-password")
                .expectStatus().isOk()
                .expectBody()
                .returnResult().getResponseBody());

        restTestClient.get()
                .uri("/api/v1/admin/test")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    private void assertUnauthorizedToken(String token) {
        restTestClient.get()
                .uri("/api/v1/test/protected")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private String expiredToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("expired@example.com")
                .issuedAt(now.minusSeconds(3_600))
                .expiresAt(now.minusSeconds(1_800))
                .claim("roles", List.of("OWNER"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String forgedToken() throws Exception {
        var forgedSecret = new SecretKeySpec(new byte[32], "HmacSHA256");
        JwtEncoder forgedEncoder = NimbusJwtEncoder.withSecretKey(forgedSecret).build();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("forged@example.com")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("ADMIN"))
                .build();
        return forgedEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private void registerOwner(String email, String password) {
        restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "fullName", "Authentication User",
                        "email", email,
                        "password", password))
                .exchange()
                .expectStatus().isCreated();
    }

    private void createUser(String email, String password, String status, String role) {
        Long userId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, full_name, status, created_at, updated_at, version)
                values (?, ?, 'Authentication User', ?, current_timestamp, current_timestamp, 0)
                returning id
                """, Long.class, email, passwordEncoder.encode(password), status);
        jdbcTemplate.update("""
                insert into user_roles (user_id, role_id)
                select ?, id from roles where name = ?
                """, userId, role);
    }

    private RestTestClient.ResponseSpec login(String email, String password) {
        return restTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange();
    }

    private String accessToken(byte[] responseBody) throws Exception {
        return json(responseBody).get("accessToken").toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] responseBody) throws Exception {
        assertThat(responseBody).isNotNull();
        return objectMapper.readValue(new String(responseBody, StandardCharsets.UTF_8), Map.class);
    }

    @RestController
    static class SecurityProbeController {

        @GetMapping("/api/v1/test/protected")
        Map<String, Boolean> protectedEndpoint() {
            return Map.of("authenticated", true);
        }

        @GetMapping("/api/v1/admin/test")
        Map<String, Boolean> adminEndpoint() {
            return Map.of("admin", true);
        }
    }
}
