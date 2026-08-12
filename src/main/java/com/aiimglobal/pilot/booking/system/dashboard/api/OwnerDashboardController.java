package com.aiimglobal.pilot.booking.system.dashboard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.dashboard.application.DashboardService;
import com.aiimglobal.pilot.booking.system.dashboard.dto.OwnerDashboardResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Owner dashboard", description = "Booking summary for the authenticated owner.")
@PreAuthorize("hasRole('OWNER')")
public class OwnerDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get the owner's dashboard")
    OwnerDashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.ownerDashboard(jwt.getSubject());
    }
}
