package com.aiimglobal.pilot.booking.system.booking.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.assignment.application.AssignmentService;
import com.aiimglobal.pilot.booking.system.assignment.dto.AssignPilotRequest;
import com.aiimglobal.pilot.booking.system.assignment.dto.AssignmentResponse;
import com.aiimglobal.pilot.booking.system.booking.application.BookingService;
import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.booking.dto.AdminBookingResponse;
import com.aiimglobal.pilot.booking.system.booking.dto.RejectBookingRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;
    private final AssignmentService assignmentService;

    @GetMapping
    List<AdminBookingResponse> list(
            @RequestParam(defaultValue = "PENDING_APPROVAL") BookingStatus status) {
        return bookingService.listForReview(status);
    }

    @PostMapping("/{id}/approve")
    AdminBookingResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.approve(jwt.getSubject(), id);
    }

    @PostMapping("/{id}/reject")
    AdminBookingResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody RejectBookingRequest request) {
        return bookingService.reject(jwt.getSubject(), id, request.reason());
    }

    @PostMapping("/{id}/assign-pilot")
    ResponseEntity<AssignmentResponse> assignPilot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody AssignPilotRequest request) {
        AssignmentResponse response = assignmentService.assign(jwt.getSubject(), id, request);
        return ResponseEntity.created(URI.create(
                        "/api/v1/admin/bookings/" + id + "/assignments/" + response.id()))
                .body(response);
    }

    @PostMapping("/{id}/complete")
    AssignmentResponse complete(@PathVariable Long id) {
        return assignmentService.complete(id);
    }
}
