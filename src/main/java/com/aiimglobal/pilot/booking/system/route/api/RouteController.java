package com.aiimglobal.pilot.booking.system.route.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.route.application.RouteService;
import com.aiimglobal.pilot.booking.system.route.dto.RouteResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    List<RouteResponse> list() {
        return routeService.listActive();
    }

    @GetMapping("/{id}")
    RouteResponse get(@PathVariable Long id) {
        return routeService.getActive(id);
    }
}
