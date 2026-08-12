package com.aiimglobal.pilot.booking.system.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.aiimglobal.pilot.booking.system.coupon.domain.Coupon;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon_redemptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false, unique = true, updatable = false)
    private Coupon coupon;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true, updatable = false)
    private Payment payment;

    @Column(name = "amount_redeemed", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRedeemed;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private Instant redeemedAt;

    private CouponRedemption(Coupon coupon, Payment payment, BigDecimal amountRedeemed) {
        this.coupon = coupon;
        this.payment = payment;
        this.amountRedeemed = amountRedeemed;
    }

    public static CouponRedemption record(Coupon coupon, Payment payment, BigDecimal amountRedeemed) {
        return new CouponRedemption(coupon, payment, amountRedeemed);
    }

    @PrePersist
    void onCreate() {
        redeemedAt = Instant.now();
    }
}
