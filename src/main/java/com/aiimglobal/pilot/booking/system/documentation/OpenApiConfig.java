package com.aiimglobal.pilot.booking.system.documentation;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Pilot Booking System API",
                version = "v1",
                description = "REST API for vessel owners, pilot bookings, payments, and administration.",
                contact = @Contact(name = "AIIM Global")),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT access token returned by the login endpoint.")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
