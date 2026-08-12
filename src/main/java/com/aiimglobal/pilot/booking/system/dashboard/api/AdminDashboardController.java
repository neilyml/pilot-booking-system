package com.aiimglobal.pilot.booking.system.dashboard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.dashboard.application.DashboardService;
import com.aiimglobal.pilot.booking.system.dashboard.dto.AdminDashboardResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin dashboard", description = "Administrative booking and operations summary.")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get the admin dashboard")
    AdminDashboardResponse dashboard() {
        return dashboardService.adminDashboard();
    }
}
