package com.aiimglobal.pilot.booking.system.booking.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.route.domain.Route;
import com.aiimglobal.pilot.booking.system.user.domain.User;
import com.aiimglobal.pilot.booking.system.vessel.domain.Vessel;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_number", nullable = false, unique = true, length = 80)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vessel_id", nullable = false, updatable = false)
    private Vessel vessel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false, updatable = false)
    private Route route;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Column(name = "service_fee", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal serviceFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookingStatus status;

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

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private Booking(
            String bookingNumber,
            User requestedBy,
            Vessel vessel,
            Route route,
            LocalDate serviceDate) {
        this.bookingNumber = bookingNumber;
        this.requestedBy = requestedBy;
        this.vessel = vessel;
        this.route = route;
        this.serviceDate = serviceDate;
        this.serviceFee = route.getServiceFee();
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    public static Booking request(
            String bookingNumber,
            User requestedBy,
            Vessel vessel,
            Route route,
            LocalDate serviceDate) {
        return new Booking(bookingNumber, requestedBy, vessel, route, serviceDate);
    }

    public void markPaid() {
        if (status != BookingStatus.PENDING_PAYMENT) {
            throw new ResourceConflictException(
                    "BOOKING_NOT_PENDING_PAYMENT", "The booking is not awaiting payment.");
        }
        status = BookingStatus.PENDING_APPROVAL;
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
