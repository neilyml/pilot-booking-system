package com.aiimglobal.pilot.booking.system.route.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.api.PageRequests;
import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.route.application.RouteService;
import com.aiimglobal.pilot.booking.system.route.dto.RouteResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Owner routes", description = "Browse active service routes.")
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    @Operation(summary = "List active routes")
    PageResponse<RouteResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return routeService.listActive(PageRequests.newestFirst(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active route")
    RouteResponse get(@PathVariable Long id) {
        return routeService.getActive(id);
    }
}
