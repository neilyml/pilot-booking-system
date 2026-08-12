package com.aiimglobal.pilot.booking.system.coupon.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.api.PageRequests;
import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.coupon.application.CouponService;
import com.aiimglobal.pilot.booking.system.coupon.domain.CouponStatus;
import com.aiimglobal.pilot.booking.system.coupon.dto.CouponResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    PageResponse<CouponResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return couponService.listForOwner(
                jwt.getSubject(), status, PageRequests.newestFirst(page, size));
    }
}
