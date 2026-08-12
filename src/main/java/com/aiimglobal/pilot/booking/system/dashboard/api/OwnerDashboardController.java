package com.aiimglobal.pilot.booking.system.dashboard.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class OwnerDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    OwnerDashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.ownerDashboard(jwt.getSubject());
    }
}
