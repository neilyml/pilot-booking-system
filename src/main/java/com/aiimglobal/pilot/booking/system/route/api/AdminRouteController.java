package com.aiimglobal.pilot.booking.system.route.api;

import java.net.URI;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.route.application.RouteService;
import com.aiimglobal.pilot.booking.system.route.dto.RouteRequest;
import com.aiimglobal.pilot.booking.system.route.dto.RouteResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/routes")
@RequiredArgsConstructor
@Tag(name = "Admin routes", description = "Create, update, and control service routes.")
public class AdminRouteController {

    private final RouteService routeService;

    @PostMapping
    @Operation(summary = "Create a route")
    ResponseEntity<RouteResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RouteRequest request) {
        RouteResponse response = routeService.create(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/routes/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a route")
    RouteResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody RouteRequest request) {
        return routeService.update(jwt.getSubject(), id, request);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a route")
    RouteResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return routeService.activate(jwt.getSubject(), id);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a route")
    RouteResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return routeService.deactivate(jwt.getSubject(), id);
    }
}
