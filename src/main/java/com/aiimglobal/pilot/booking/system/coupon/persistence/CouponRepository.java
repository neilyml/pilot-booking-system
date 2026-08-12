package com.aiimglobal.pilot.booking.system.coupon.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aiimglobal.pilot.booking.system.coupon.domain.Coupon;
import com.aiimglobal.pilot.booking.system.coupon.domain.CouponStatus;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Page<Coupon> findAllByOwnerEmail(String ownerEmail, Pageable pageable);

    Page<Coupon> findAllByOwnerEmailAndStatus(
            String ownerEmail, CouponStatus status, Pageable pageable);

    long countByOwnerEmailAndStatus(String ownerEmail, CouponStatus status);

    long countByStatus(CouponStatus status);

    long countByOwnerEmailAndStatusAndExpiresAtAfter(
            String ownerEmail, CouponStatus status, Instant currentTime);

    @Query("""
            select sum(coupon.amount) from Coupon coupon
            where coupon.owner.email = :ownerEmail
              and coupon.status = :status
              and coupon.expiresAt > :currentTime
            """)
    BigDecimal sumAvailableValue(
            @Param("ownerEmail") String ownerEmail,
            @Param("status") CouponStatus status,
            @Param("currentTime") Instant currentTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select coupon from Coupon coupon
            where coupon.code = :code and coupon.owner.id = :ownerId
            """)
    Optional<Coupon> findForUpdateByCodeAndOwnerId(
            @Param("code") String code, @Param("ownerId") Long ownerId);
}
