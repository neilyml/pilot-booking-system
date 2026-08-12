package com.aiimglobal.pilot.booking.system.payment.application;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.booking.persistence.BookingRepository;
import com.aiimglobal.pilot.booking.system.coupon.domain.CouponStatus;
import com.aiimglobal.pilot.booking.system.coupon.persistence.CouponRepository;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.payment.domain.CouponRedemption;
import com.aiimglobal.pilot.booking.system.payment.domain.Payment;
import com.aiimglobal.pilot.booking.system.payment.dto.CouponPaymentRequest;
import com.aiimglobal.pilot.booking.system.payment.dto.CouponPaymentResponse;
import com.aiimglobal.pilot.booking.system.payment.persistence.CouponRedemptionRepository;
import com.aiimglobal.pilot.booking.system.payment.persistence.PaymentRepository;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponPaymentService {

    private final BookingRepository bookingRepository;
    private final CouponRepository couponRepository;
    private final PaymentRepository paymentRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public CouponPaymentResponse redeem(
            String payerEmail, Long bookingId, CouponPaymentRequest request) {
        var payer = userRepository.findByEmail(payerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated payer does not exist."));
        var booking = bookingRepository.findForUpdateByIdAndRequestedById(bookingId, payer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND", "Booking was not found."));
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ResourceConflictException(
                    "BOOKING_NOT_PENDING_PAYMENT", "The booking is not awaiting payment.");
        }

        var coupon = couponRepository.findForUpdateByCodeAndOwnerId(request.couponCode(), payer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COUPON_NOT_FOUND", "Coupon was not found."));
        Instant now = Instant.now();
        if (coupon.getStatus() != CouponStatus.ACTIVE || !coupon.getExpiresAt().isAfter(now)) {
            throw new ResourceConflictException(
                    "COUPON_NOT_REDEEMABLE", "The coupon is not redeemable.");
        }
        if (coupon.getAmount().compareTo(booking.getServiceFee()) < 0) {
            throw new ResourceConflictException(
                    "COUPON_INSUFFICIENT_VALUE", "The coupon value does not cover the booking fee.");
        }

        var payment = paymentRepository.saveAndFlush(Payment.successfulCoupon(
                booking, payer, booking.getServiceFee(), transactionReference()));
        var redemption = redemptionRepository.saveAndFlush(CouponRedemption.record(
                coupon, payment, booking.getServiceFee()));
        coupon.markUsed(now);
        booking.markPaid();
        couponRepository.save(coupon);
        bookingRepository.saveAndFlush(booking);
        return toResponse(payment, redemption);
    }

    private static String transactionReference() {
        return "PAY-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private static CouponPaymentResponse toResponse(
            Payment payment, CouponRedemption redemption) {
        var coupon = redemption.getCoupon();
        return new CouponPaymentResponse(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getPayer().getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getTransactionReference(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                redemption.getId(),
                coupon.getId(),
                coupon.getCode(),
                redemption.getAmountRedeemed(),
                redemption.getRedeemedAt());
    }
}
