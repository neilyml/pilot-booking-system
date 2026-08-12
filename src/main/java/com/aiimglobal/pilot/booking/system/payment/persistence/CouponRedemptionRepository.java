package com.aiimglobal.pilot.booking.system.payment.persistence;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aiimglobal.pilot.booking.system.payment.domain.CouponRedemption;
import com.aiimglobal.pilot.booking.system.payment.domain.PaymentStatus;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    @Query("""
            select sum(redemption.amountRedeemed) from CouponRedemption redemption
            where redemption.payment.status = :paymentStatus
            """)
    BigDecimal sumAmountRedeemedByPaymentStatus(
            @Param("paymentStatus") PaymentStatus paymentStatus);
}
