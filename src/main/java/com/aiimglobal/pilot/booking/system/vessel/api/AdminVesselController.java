package com.aiimglobal.pilot.booking.system.vessel.api;

import com.aiimglobal.pilot.booking.system.vessel.application.VesselService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/vessels")
@RequiredArgsConstructor
public class AdminVesselController {

    private final VesselService vesselService;

    @GetMapping
    List<AdminVesselResponse> list(
            @RequestParam(defaultValue = "PENDING") VesselStatus status) {
        return vesselService.listForReview(status);
    }

    @PostMapping("/{id}/approve")
    AdminVesselResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return vesselService.approve(jwt.getSubject(), id);
    }

    @PostMapping("/{id}/reject")
    AdminVesselResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody RejectVesselRequest request) {
        return vesselService.reject(jwt.getSubject(), id, request.reason());
    }
}
