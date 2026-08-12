package com.aiimglobal.pilot.booking.system.vessel.api;

import java.net.URI;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.api.PageRequests;
import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.vessel.application.VesselService;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;
import com.aiimglobal.pilot.booking.system.vessel.dto.RegisterVesselRequest;
import com.aiimglobal.pilot.booking.system.vessel.dto.VesselResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/vessels")
@RequiredArgsConstructor
@Tag(name = "Owner vessels", description = "Register and track vessels owned by the authenticated owner.")
public class VesselController {

    private final VesselService vesselService;

    @PostMapping
    @Operation(summary = "Register a vessel")
    ResponseEntity<VesselResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterVesselRequest request) {
        VesselResponse response = vesselService.register(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/vessels/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List the owner's vessels")
    PageResponse<VesselResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) VesselStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return vesselService.list(jwt.getSubject(), status, PageRequests.newestFirst(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an owned vessel")
    VesselResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return vesselService.get(jwt.getSubject(), id);
    }
}
