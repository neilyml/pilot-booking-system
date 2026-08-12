package com.aiimglobal.pilot.booking.system.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;
import com.aiimglobal.pilot.booking.system.user.domain.Role;
import com.aiimglobal.pilot.booking.system.user.domain.RoleName;
import com.aiimglobal.pilot.booking.system.user.domain.UserStatus;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

class OwnerRegistrationIT extends IntegrationTestBase {

    private static final String REGISTRATION_PATH = "/api/v1/auth/register";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registersActiveOwnerWithNormalizedEmailAndEncodedPassword() {
        register(validOwner("  OWNER@Example.COM  ", "+8801712345678"))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/auth/register/[0-9]+")
                .expectBody()
                .jsonPath("$.email").isEqualTo("owner@example.com")
                .jsonPath("$.phone").isEqualTo("+8801712345678")
                .jsonPath("$.fullName").isEqualTo("Vessel Owner")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.roles[0]").isEqualTo("OWNER")
                .jsonPath("$.password").doesNotExist()
                .jsonPath("$.passwordHash").doesNotExist();

        var user = userRepository.findWithRolesByEmail("owner@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly(RoleName.OWNER);
        assertThat(user.getPasswordHash()).isNotEqualTo("strong-password");
        assertThat(passwordEncoder.matches("strong-password", user.getPasswordHash())).isTrue();
    }

    @Test
    void registersOwnerWithoutOptionalPhone() {
        register(Map.of(
                "fullName", "Phone Free Owner",
                "email", "phone-free@example.com",
                "password", "strong-password"))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.phone").doesNotExist();

        assertThat(userRepository.findByEmail("phone-free@example.com").orElseThrow().getPhone()).isNull();
    }

    @Test
    void rejectsDuplicateNormalizedEmailWithoutCreatingAnotherUser() {
        register(validOwner("owner@example.com", "+8801711111111")).expectStatus().isCreated();

        register(validOwner("  OWNER@EXAMPLE.COM ", "+8801722222222"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("EMAIL_ALREADY_REGISTERED");

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateNonNullPhone() {
        register(validOwner("first@example.com", "+8801711111111")).expectStatus().isCreated();

        register(validOwner("second@example.com", "  +8801711111111  "))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PHONE_ALREADY_REGISTERED");

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void reportsFieldErrorsAndPersistsNothingForInvalidInput() {
        register(Map.of(
                "fullName", "  ",
                "email", "not-an-email",
                "password", "short"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("email")
                .jsonPath("$.fieldErrors[1].field").isEqualTo("fullName")
                .jsonPath("$.fieldErrors[2].field").isEqualTo("password");

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void ignoresClientControlledRoleAndStatusFields() {
        register("""
                {
                  "fullName": "Untrusted Owner",
                  "email": "untrusted@example.com",
                  "phone": "+8801712345678",
                  "password": "strong-password",
                  "role": "ADMIN",
                  "roles": ["ADMIN"],
                  "status": "DISABLED",
                  "admin": true
                }
                """)
                .expectStatus().isCreated();

        var user = userRepository.findWithRolesByEmail("untrusted@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly(RoleName.OWNER);
    }

    @Test
    void rollsBackUserWhenOwnerRoleCannotBeAssigned() {
        jdbcTemplate.update("delete from roles where name = 'OWNER'");
        try {
            register(validOwner("atomic@example.com", "+8801712345678"))
                    .expectStatus().is5xxServerError()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("INTERNAL_SERVER_ERROR");

            assertThat(userRepository.count()).isZero();
        } finally {
            jdbcTemplate.update("insert into roles (name) values ('OWNER') on conflict (name) do nothing");
        }
    }

    private RestTestClient.ResponseSpec register(Object request) {
        return restTestClient.post()
                .uri(REGISTRATION_PATH)
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private Map<String, String> validOwner(String email, String phone) {
        return Map.of(
                "fullName", "Vessel Owner",
                "email", email,
                "phone", phone,
                "password", "strong-password");
    }
}
