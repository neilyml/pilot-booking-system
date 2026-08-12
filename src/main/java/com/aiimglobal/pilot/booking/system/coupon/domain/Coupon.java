package com.aiimglobal.pilot.booking.system.coupon.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.aiimglobal.pilot.booking.system.user.domain.User;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CouponStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false, updatable = false)
    private User issuedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private Coupon(String code, User owner, BigDecimal amount, Instant expiresAt, User issuedBy) {
        this.code = code;
        this.owner = owner;
        this.amount = amount;
        this.expiresAt = expiresAt;
        this.issuedBy = issuedBy;
        this.status = CouponStatus.ACTIVE;
    }

    public static Coupon issue(
            String code,
            User owner,
            BigDecimal amount,
            Instant expiresAt,
            User issuedBy) {
        return new Coupon(code, owner, amount, expiresAt, issuedBy);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
