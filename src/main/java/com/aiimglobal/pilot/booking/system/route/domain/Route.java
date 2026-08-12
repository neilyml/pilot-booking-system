package com.aiimglobal.pilot.booking.system.route.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.aiimglobal.pilot.booking.system.user.domain.User;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "routes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String origin;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(name = "service_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceFee;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private Route(
            String code,
            String name,
            String origin,
            String destination,
            BigDecimal serviceFee,
            User createdBy) {
        this.code = code;
        this.name = name;
        this.origin = origin;
        this.destination = destination;
        this.serviceFee = serviceFee;
        this.createdBy = createdBy;
        this.active = true;
    }

    public static Route create(
            String code,
            String name,
            String origin,
            String destination,
            BigDecimal serviceFee,
            User createdBy) {
        return new Route(code, name, origin, destination, serviceFee, createdBy);
    }

    public void update(
            String code,
            String name,
            String origin,
            String destination,
            BigDecimal serviceFee) {
        this.code = code;
        this.name = name;
        this.origin = origin;
        this.destination = destination;
        this.serviceFee = serviceFee;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
