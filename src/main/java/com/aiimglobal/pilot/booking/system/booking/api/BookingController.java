package com.aiimglobal.pilot.booking.system.booking.api;

import java.net.URI;

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

import com.aiimglobal.pilot.booking.system.api.PageRequests;
import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.booking.application.BookingService;
import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse;
import com.aiimglobal.pilot.booking.system.booking.dto.CreateBookingRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.create(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + response.id())).body(response);
    }

    @GetMapping
    PageResponse<BookingResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookingService.list(
                jwt.getSubject(), status, PageRequests.newestFirst(page, size));
    }

    @GetMapping("/{id}")
    BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.get(jwt.getSubject(), id);
    }
}
