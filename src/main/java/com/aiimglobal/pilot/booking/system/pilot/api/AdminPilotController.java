package com.aiimglobal.pilot.booking.system.pilot.api;

import java.net.URI;
import java.time.LocalDate;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.api.PageRequests;
import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.pilot.application.PilotService;
import com.aiimglobal.pilot.booking.system.pilot.domain.PilotStatus;
import com.aiimglobal.pilot.booking.system.pilot.dto.CreatePilotRequest;
import com.aiimglobal.pilot.booking.system.pilot.dto.PilotResponse;
import com.aiimglobal.pilot.booking.system.pilot.dto.UpdatePilotRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/pilots")
@RequiredArgsConstructor
@Tag(name = "Admin pilots", description = "Manage pilots and find availability for assignments.")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPilotController {

    private final PilotService pilotService;

    @GetMapping
    @Operation(summary = "List pilots")
    PageResponse<PilotResponse> list(
            @RequestParam(required = false) PilotStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pilotService.list(status, PageRequests.newestFirst(page, size));
    }

    @GetMapping("/available")
    @Operation(summary = "List pilots available on a service date")
    PageResponse<PilotResponse> available(
            @RequestParam LocalDate serviceDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pilotService.available(serviceDate, PageRequests.newestFirst(page, size));
    }

    @PostMapping
    @Operation(summary = "Create a pilot")
    ResponseEntity<PilotResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePilotRequest request) {
        PilotResponse response = pilotService.create(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/pilots/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a pilot")
    PilotResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePilotRequest request) {
        return pilotService.update(jwt.getSubject(), id, request);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a pilot")
    PilotResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return pilotService.deactivate(jwt.getSubject(), id);
    }
}
