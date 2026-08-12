package com.aiimglobal.pilot.booking.system.booking.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.assignment.domain.AssignmentStatus;
import com.aiimglobal.pilot.booking.system.assignment.persistence.BookingAssignmentRepository;
import com.aiimglobal.pilot.booking.system.booking.domain.Booking;
import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.booking.dto.AdminBookingResponse;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.AssignmentSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.PaymentSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.RouteSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.VesselSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.CreateBookingRequest;
import com.aiimglobal.pilot.booking.system.booking.persistence.BookingRepository;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.payment.domain.PaymentStatus;
import com.aiimglobal.pilot.booking.system.payment.persistence.PaymentRepository;
import com.aiimglobal.pilot.booking.system.route.persistence.RouteRepository;
import com.aiimglobal.pilot.booking.system.user.domain.User;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;
import com.aiimglobal.pilot.booking.system.vessel.persistence.VesselRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VesselRepository vesselRepository;
    private final RouteRepository routeRepository;
    private final PaymentRepository paymentRepository;
    private final BookingAssignmentRepository assignmentRepository;

    @Transactional
    public BookingResponse create(String requesterEmail, CreateBookingRequest request) {
        User requester = requester(requesterEmail);
        var vessel = vesselRepository.findByIdAndOwnerId(request.vesselId(), requester.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_VESSEL_NOT_FOUND", "Vessel was not found."));
        if (vessel.getStatus() != VesselStatus.APPROVED) {
            throw new ResourceConflictException(
                    "BOOKING_VESSEL_NOT_APPROVED", "Only an approved vessel can be booked.");
        }
        var route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_ROUTE_NOT_FOUND", "Route was not found."));
        if (!route.isActive()) {
            throw new ResourceConflictException(
                    "BOOKING_ROUTE_INACTIVE", "Only an active route can be booked.");
        }

        var booking = Booking.request(
                bookingNumber(), requester, vessel, route, request.serviceDate());
        return toResponse(bookingRepository.saveAndFlush(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(String requesterEmail) {
        Long requesterId = requester(requesterEmail).getId();
        return bookingRepository.findAllByRequestedByIdOrderById(requesterId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(String requesterEmail, Long bookingId) {
        Long requesterId = requester(requesterEmail).getId();
        return bookingRepository.findByIdAndRequestedById(bookingId, requesterId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND", "Booking was not found."));
    }

    @Transactional(readOnly = true)
    public List<AdminBookingResponse> listForReview(BookingStatus status) {
        return bookingRepository.findAllByStatusOrderById(status).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional
    public AdminBookingResponse approve(String reviewerEmail, Long bookingId) {
        var booking = bookingForReview(bookingId);
        booking.approve(reviewer(reviewerEmail));
        return saveReviewed(booking);
    }

    @Transactional
    public AdminBookingResponse reject(String reviewerEmail, Long bookingId, String reason) {
        var booking = bookingForReview(bookingId);
        booking.reject(reviewer(reviewerEmail), reason);
        return saveReviewed(booking);
    }

    private User requester(String requesterEmail) {
        return userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated requester does not exist."));
    }

    private User reviewer(String reviewerEmail) {
        return userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated reviewer does not exist."));
    }

    private Booking bookingForReview(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND", "Booking was not found."));
    }

    private AdminBookingResponse saveReviewed(Booking booking) {
        try {
            return toAdminResponse(bookingRepository.saveAndFlush(booking));
        } catch (OptimisticLockingFailureException exception) {
            throw new ResourceConflictException(
                    "BOOKING_REVIEW_CONFLICT", "The booking was reviewed concurrently.");
        }
    }

    private static String bookingNumber() {
        return "BKG-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private BookingResponse toResponse(Booking booking) {
        var vessel = booking.getVessel();
        var route = booking.getRoute();
        var payment = paymentRepository.findByBookingIdAndStatus(
                booking.getId(), PaymentStatus.SUCCESS);
        var assignment = assignmentRepository.findByBookingIdAndStatus(
                booking.getId(), AssignmentStatus.ACTIVE);
        return new BookingResponse(
                booking.getId(),
                booking.getBookingNumber(),
                booking.getRequestedBy().getId(),
                booking.getServiceDate(),
                booking.getServiceFee(),
                booking.getStatus(),
                new VesselSummary(
                        vessel.getId(),
                        vessel.getName(),
                        vessel.getRegistrationNumber(),
                        vessel.getVesselType(),
                        vessel.getStatus()),
                new RouteSummary(
                        route.getId(),
                        route.getCode(),
                        route.getName(),
                        route.getOrigin(),
                        route.getDestination()),
                payment.map(value -> new PaymentSummary(
                                value.getId(),
                                value.getStatus().name(),
                                value.getAmount(),
                                value.getPaidAt()))
                        .orElse(null),
                assignment.map(value -> new AssignmentSummary(
                                value.getId(),
                                value.getStatus().name(),
                                value.getPilot().getId(),
                                value.getPilot().getName()))
                        .orElse(null),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getCompletedAt());
    }

    private AdminBookingResponse toAdminResponse(Booking booking) {
        var vessel = booking.getVessel();
        var route = booking.getRoute();
        var reviewer = booking.getReviewedBy();
        var payment = paymentRepository.findByBookingIdAndStatus(
                booking.getId(), PaymentStatus.SUCCESS);
        var assignment = assignmentRepository.findByBookingIdAndStatus(
                booking.getId(), AssignmentStatus.ACTIVE);
        return new AdminBookingResponse(
                booking.getId(),
                booking.getBookingNumber(),
                booking.getRequestedBy().getId(),
                booking.getRequestedBy().getEmail(),
                booking.getServiceDate(),
                booking.getServiceFee(),
                booking.getStatus(),
                new VesselSummary(
                        vessel.getId(),
                        vessel.getName(),
                        vessel.getRegistrationNumber(),
                        vessel.getVesselType(),
                        vessel.getStatus()),
                new RouteSummary(
                        route.getId(),
                        route.getCode(),
                        route.getName(),
                        route.getOrigin(),
                        route.getDestination()),
                payment.map(value -> new PaymentSummary(
                                value.getId(),
                                value.getStatus().name(),
                                value.getAmount(),
                                value.getPaidAt()))
                        .orElse(null),
                assignment.map(value -> new AssignmentSummary(
                                value.getId(),
                                value.getStatus().name(),
                                value.getPilot().getId(),
                                value.getPilot().getName()))
                        .orElse(null),
                reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : reviewer.getEmail(),
                booking.getReviewedAt(),
                booking.getRejectionReason(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getCompletedAt());
    }
}
