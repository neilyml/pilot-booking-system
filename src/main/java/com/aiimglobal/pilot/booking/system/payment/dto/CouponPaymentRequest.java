package com.aiimglobal.pilot.booking.system.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CouponPaymentRequest(
        @NotBlank @Size(max = 80) String couponCode) {
}
