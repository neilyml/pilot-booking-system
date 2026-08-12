package com.aiimglobal.pilot.booking.system.dashboard.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.dashboard.application.DashboardService;
import com.aiimglobal.pilot.booking.system.dashboard.dto.AdminDashboardResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    AdminDashboardResponse dashboard() {
        return dashboardService.adminDashboard();
    }
}
