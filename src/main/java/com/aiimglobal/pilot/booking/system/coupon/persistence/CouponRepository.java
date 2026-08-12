package com.aiimglobal.pilot.booking.system.coupon.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.coupon.domain.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    List<Coupon> findAllByOwnerEmailOrderById(String ownerEmail);
}
