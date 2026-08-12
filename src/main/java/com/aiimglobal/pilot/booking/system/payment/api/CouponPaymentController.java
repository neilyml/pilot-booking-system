package com.aiimglobal.pilot.booking.system.payment.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.payment.application.CouponPaymentService;
import com.aiimglobal.pilot.booking.system.payment.dto.CouponPaymentRequest;
import com.aiimglobal.pilot.booking.system.payment.dto.CouponPaymentResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/payments")
@RequiredArgsConstructor
public class CouponPaymentController {

    private final CouponPaymentService couponPaymentService;

    @PostMapping("/coupon")
    ResponseEntity<CouponPaymentResponse> redeem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long bookingId,
            @Valid @RequestBody CouponPaymentRequest request) {
        CouponPaymentResponse response = couponPaymentService.redeem(
                jwt.getSubject(), bookingId, request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + response.id())).body(response);
    }
}
