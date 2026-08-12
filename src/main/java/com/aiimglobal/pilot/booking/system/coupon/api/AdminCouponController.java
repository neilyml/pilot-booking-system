package com.aiimglobal.pilot.booking.system.coupon.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.coupon.application.CouponService;
import com.aiimglobal.pilot.booking.system.coupon.dto.CouponResponse;
import com.aiimglobal.pilot.booking.system.coupon.dto.IssueCouponRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    @PostMapping
    ResponseEntity<CouponResponse> issue(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody IssueCouponRequest request) {
        CouponResponse response = couponService.issue(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/coupons/" + response.id())).body(response);
    }
}
