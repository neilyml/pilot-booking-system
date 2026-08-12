package com.aiimglobal.pilot.booking.system.security;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("application.security")
public record EndpointSecurityProperties(
        List<@Valid PublicEndpoint> publicEndpoints) {

    public EndpointSecurityProperties {
        publicEndpoints = publicEndpoints == null
                ? List.of()
                : List.copyOf(publicEndpoints);
    }

    public record PublicEndpoint(
            HttpMethod method,
            @NotBlank String pattern) {
    }
}
