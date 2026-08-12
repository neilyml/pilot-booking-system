package com.aiimglobal.pilot.booking.system.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.payment.domain.CouponRedemption;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
}
