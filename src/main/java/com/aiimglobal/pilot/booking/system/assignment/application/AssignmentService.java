package com.aiimglobal.pilot.booking.system.assignment.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.assignment.domain.AssignmentStatus;
import com.aiimglobal.pilot.booking.system.assignment.domain.BookingAssignment;
import com.aiimglobal.pilot.booking.system.assignment.dto.AssignPilotRequest;
import com.aiimglobal.pilot.booking.system.assignment.dto.AssignmentResponse;
import com.aiimglobal.pilot.booking.system.assignment.persistence.BookingAssignmentRepository;
import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.booking.persistence.BookingRepository;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.pilot.domain.PilotStatus;
import com.aiimglobal.pilot.booking.system.pilot.persistence.PilotRepository;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final BookingAssignmentRepository assignmentRepository;
    private final BookingRepository bookingRepository;
    private final PilotRepository pilotRepository;
    private final UserRepository userRepository;

    @Transactional
    public AssignmentResponse assign(
            String administratorEmail, Long bookingId, AssignPilotRequest request) {
        var booking = bookingRepository.findForUpdateById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND", "Booking was not found."));
        if (assignmentRepository.existsByBookingIdAndStatus(bookingId, AssignmentStatus.ACTIVE)) {
            throw new ResourceConflictException(
                    "BOOKING_ALREADY_ASSIGNED", "The booking already has an active pilot assignment.");
        }
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new ResourceConflictException(
                    "BOOKING_NOT_APPROVED", "Only an approved booking can receive a pilot.");
        }

        var pilot = pilotRepository.findForUpdateById(request.pilotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PILOT_NOT_FOUND", "Pilot was not found."));
        if (pilot.getStatus() != PilotStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "PILOT_INACTIVE", "Only an active pilot can be assigned.");
        }
        if (assignmentRepository.existsByPilotIdAndServiceDateAndStatus(
                pilot.getId(), booking.getServiceDate(), AssignmentStatus.ACTIVE)) {
            throw new ResourceConflictException(
                    "PILOT_NOT_AVAILABLE", "The pilot is already assigned on the service date.");
        }
        var administrator = userRepository.findByEmail(administratorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated administrator does not exist."));

        try {
            var assignment = assignmentRepository.saveAndFlush(
                    BookingAssignment.assign(booking, pilot, administrator));
            booking.markAssigned();
            bookingRepository.saveAndFlush(booking);
            return toResponse(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                    "ASSIGNMENT_CONFLICT", "The booking or pilot was assigned concurrently.");
        }
    }

    public static AssignmentResponse toResponse(BookingAssignment assignment) {
        var pilot = assignment.getPilot();
        var assignedBy = assignment.getAssignedBy();
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getBooking().getId(),
                pilot.getId(),
                pilot.getEmployeeNumber(),
                pilot.getName(),
                assignment.getServiceDate(),
                assignedBy.getId(),
                assignedBy.getEmail(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getCompletedAt());
    }
}
