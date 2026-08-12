package com.aiimglobal.pilot.booking.system.assignment.persistence;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.assignment.domain.AssignmentStatus;
import com.aiimglobal.pilot.booking.system.assignment.domain.BookingAssignment;

public interface BookingAssignmentRepository extends JpaRepository<BookingAssignment, Long> {

    boolean existsByBookingIdAndStatus(Long bookingId, AssignmentStatus status);

    boolean existsByPilotIdAndServiceDateAndStatus(
            Long pilotId, LocalDate serviceDate, AssignmentStatus status);

    boolean existsByPilotIdAndStatus(Long pilotId, AssignmentStatus status);

    Optional<BookingAssignment> findByBookingIdAndStatus(Long bookingId, AssignmentStatus status);
}
