package com.aiimglobal.pilot.booking.system.vessel.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.aiimglobal.pilot.booking.system.user.domain.User;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vessels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vessel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "registration_number", nullable = false, unique = true, length = 100)
    private String registrationNumber;

    @Column(name = "vessel_type", nullable = false, length = 80)
    private String vesselType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VesselStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private Vessel(User owner, String name, String registrationNumber, String vesselType) {
        this.owner = owner;
        this.name = name;
        this.registrationNumber = registrationNumber;
        this.vesselType = vesselType;
        this.status = VesselStatus.PENDING;
    }

    public static Vessel register(User owner, String name, String registrationNumber, String vesselType) {
        return new Vessel(owner, name, registrationNumber, vesselType);
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
