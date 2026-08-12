package com.aiimglobal.pilot.booking.system.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.aiimglobal.pilot.booking.system.payment.domain.PaymentMethod;
import com.aiimglobal.pilot.booking.system.payment.domain.PaymentStatus;

public record CouponPaymentResponse(
        Long id,
        Long bookingId,
        Long payerId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String transactionReference,
        Instant paidAt,
        Instant createdAt,
        Long redemptionId,
        Long couponId,
        String couponCode,
        BigDecimal amountRedeemed,
        Instant redeemedAt) {
}
