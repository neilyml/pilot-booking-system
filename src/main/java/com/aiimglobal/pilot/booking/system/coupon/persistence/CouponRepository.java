package com.aiimglobal.pilot.booking.system.coupon.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aiimglobal.pilot.booking.system.coupon.domain.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    List<Coupon> findAllByOwnerEmailOrderById(String ownerEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select coupon from Coupon coupon
            where coupon.code = :code and coupon.owner.id = :ownerId
            """)
    Optional<Coupon> findForUpdateByCodeAndOwnerId(
            @Param("code") String code, @Param("ownerId") Long ownerId);
}
