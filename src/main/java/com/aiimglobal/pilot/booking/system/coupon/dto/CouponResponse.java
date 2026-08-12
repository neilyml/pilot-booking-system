package com.aiimglobal.pilot.booking.system.coupon.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.aiimglobal.pilot.booking.system.coupon.domain.CouponStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public record CouponResponse(
        Long id,
        String code,
        Long ownerId,
        String ownerEmail,
        BigDecimal amount,
        CouponStatus status,
        Instant expiresAt,
        Long issuedById,
        String issuedByEmail,
        Instant createdAt,
        @JsonInclude(Include.NON_NULL) Instant usedAt) {
}
