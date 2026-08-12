package com.aiimglobal.pilot.booking.system.vessel.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.vessel.application.VesselService;
import com.aiimglobal.pilot.booking.system.vessel.dto.RegisterVesselRequest;
import com.aiimglobal.pilot.booking.system.vessel.dto.VesselResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/vessels")
@RequiredArgsConstructor
public class VesselController {

    private final VesselService vesselService;

    @PostMapping
    ResponseEntity<VesselResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterVesselRequest request) {
        VesselResponse response = vesselService.register(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/vessels/" + response.id())).body(response);
    }

    @GetMapping
    List<VesselResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return vesselService.list(jwt.getSubject());
    }

    @GetMapping("/{id}")
    VesselResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return vesselService.get(jwt.getSubject(), id);
    }
}
