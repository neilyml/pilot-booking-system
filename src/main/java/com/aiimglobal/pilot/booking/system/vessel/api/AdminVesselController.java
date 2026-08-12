package com.aiimglobal.pilot.booking.system.vessel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.aiimglobal.pilot.booking.system.vessel.application.VesselService;
import com.aiimglobal.pilot.booking.system.api.PageRequests;
import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;
import com.aiimglobal.pilot.booking.system.vessel.dto.AdminVesselResponse;
import com.aiimglobal.pilot.booking.system.vessel.dto.RejectVesselRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/vessels")
@RequiredArgsConstructor
@Tag(name = "Admin vessels", description = "Review owner vessel registrations.")
public class AdminVesselController {

    private final VesselService vesselService;

    @GetMapping
    @Operation(summary = "List vessels for review")
    PageResponse<AdminVesselResponse> list(
            @RequestParam(defaultValue = "PENDING") VesselStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return vesselService.listForReview(status, PageRequests.newestFirst(page, size));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a vessel")
    AdminVesselResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return vesselService.approve(jwt.getSubject(), id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a vessel")
    AdminVesselResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody RejectVesselRequest request) {
        return vesselService.reject(jwt.getSubject(), id, request.reason());
    }
}
