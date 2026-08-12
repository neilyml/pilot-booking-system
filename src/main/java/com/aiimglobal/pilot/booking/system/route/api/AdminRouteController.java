package com.aiimglobal.pilot.booking.system.route.api;

import java.net.URI;

import jakarta.validation.Valid;

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
public class AdminRouteController {

    private final RouteService routeService;

    @PostMapping
    ResponseEntity<RouteResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RouteRequest request) {
        RouteResponse response = routeService.create(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/routes/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    RouteResponse update(@PathVariable Long id, @Valid @RequestBody RouteRequest request) {
        return routeService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    RouteResponse activate(@PathVariable Long id) {
        return routeService.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    RouteResponse deactivate(@PathVariable Long id) {
        return routeService.deactivate(id);
    }
}
