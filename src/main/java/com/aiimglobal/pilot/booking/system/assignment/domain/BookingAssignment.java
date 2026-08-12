package com.aiimglobal.pilot.booking.system.assignment.domain;

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
import jakarta.persistence.Table;

import com.aiimglobal.pilot.booking.system.booking.domain.Booking;
import com.aiimglobal.pilot.booking.system.pilot.domain.Pilot;
import com.aiimglobal.pilot.booking.system.user.domain.User;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pilot_id", nullable = false, updatable = false)
    private Pilot pilot;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false, updatable = false)
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentStatus status;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private BookingAssignment(Booking booking, Pilot pilot, User assignedBy) {
        this.booking = booking;
        this.pilot = pilot;
        this.serviceDate = booking.getServiceDate();
        this.assignedBy = assignedBy;
        this.status = AssignmentStatus.ACTIVE;
    }

    public static BookingAssignment assign(Booking booking, Pilot pilot, User assignedBy) {
        return new BookingAssignment(booking, pilot, assignedBy);
    }

    @PrePersist
    void onCreate() {
        assignedAt = Instant.now();
    }
}
