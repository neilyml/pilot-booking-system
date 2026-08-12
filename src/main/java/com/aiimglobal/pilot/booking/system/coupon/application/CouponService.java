package com.aiimglobal.pilot.booking.system.coupon.application;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.coupon.domain.Coupon;
import com.aiimglobal.pilot.booking.system.coupon.domain.CouponStatus;
import com.aiimglobal.pilot.booking.system.coupon.dto.CouponResponse;
import com.aiimglobal.pilot.booking.system.coupon.dto.IssueCouponRequest;
import com.aiimglobal.pilot.booking.system.coupon.persistence.CouponRepository;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.user.domain.RoleName;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private static final String CODE_PREFIX = "CPN-";

    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    @Transactional
    public CouponResponse issue(String issuerEmail, IssueCouponRequest request) {
        var owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COUPON_OWNER_NOT_FOUND", "The coupon owner was not found."));
        boolean isOwner = owner.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.OWNER);
        if (!isOwner) {
            throw new ResourceConflictException(
                    "COUPON_OWNER_NOT_ELIGIBLE", "Coupons can only be issued to vessel owners.");
        }
        var issuer = userRepository.findByEmail(issuerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated issuer does not exist."));
        var coupon = Coupon.issue(
                generateCode(), owner, request.amount(), request.expiresAt(), issuer);
        CouponResponse response = toResponse(couponRepository.saveAndFlush(coupon));
        log.info("action=coupon_issued actor={} couponId={} ownerId={}",
                issuerEmail, response.id(), response.ownerId());
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> listForOwner(
            String ownerEmail, CouponStatus status, Pageable pageable) {
        var coupons = status == null
                ? couponRepository.findAllByOwnerEmail(ownerEmail, pageable)
                : couponRepository.findAllByOwnerEmailAndStatus(ownerEmail, status, pageable);
        return PageResponse.from(coupons.map(CouponService::toResponse));
    }

    private static String generateCode() {
        return CODE_PREFIX + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private static CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getOwner().getId(),
                coupon.getOwner().getEmail(),
                coupon.getAmount(),
                coupon.getStatus(),
                coupon.getExpiresAt(),
                coupon.getIssuedBy().getId(),
                coupon.getIssuedBy().getEmail(),
                coupon.getCreatedAt(),
                coupon.getUsedAt());
    }
}
