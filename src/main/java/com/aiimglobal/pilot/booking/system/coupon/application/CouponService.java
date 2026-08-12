package com.aiimglobal.pilot.booking.system.coupon.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.coupon.domain.Coupon;
import com.aiimglobal.pilot.booking.system.coupon.dto.CouponResponse;
import com.aiimglobal.pilot.booking.system.coupon.dto.IssueCouponRequest;
import com.aiimglobal.pilot.booking.system.coupon.persistence.CouponRepository;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.user.domain.RoleName;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
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
        return toResponse(couponRepository.saveAndFlush(coupon));
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listForOwner(String ownerEmail) {
        return couponRepository.findAllByOwnerEmailOrderById(ownerEmail).stream()
                .map(CouponService::toResponse)
                .toList();
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
