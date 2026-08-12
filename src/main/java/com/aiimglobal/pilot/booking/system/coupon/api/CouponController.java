package com.aiimglobal.pilot.booking.system.coupon.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.coupon.application.CouponService;
import com.aiimglobal.pilot.booking.system.coupon.dto.CouponResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    List<CouponResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return couponService.listForOwner(jwt.getSubject());
    }
}
