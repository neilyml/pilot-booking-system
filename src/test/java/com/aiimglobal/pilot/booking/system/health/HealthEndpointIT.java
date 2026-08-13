package com.aiimglobal.pilot.booking.system.health;

import org.junit.jupiter.api.Test;

import com.aiimglobal.pilot.booking.system.support.IntegrationTestBase;

class HealthEndpointIT extends IntegrationTestBase {

    @Test
    void exposesHealthEndpointWithoutAuthentication() {
        restTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
    }

    @Test
    void exposesLivenessProbeWithoutAuthentication() {
        restTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
    }

    @Test
    void exposesReadinessProbeWithoutAuthentication() {
        restTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
    }
}
