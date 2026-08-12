package com.aiimglobal.pilot.booking.system.booking.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.booking.domain.Booking;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.RouteSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.BookingResponse.VesselSummary;
import com.aiimglobal.pilot.booking.system.booking.dto.CreateBookingRequest;
import com.aiimglobal.pilot.booking.system.booking.persistence.BookingRepository;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
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
                .map(BookingService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(String requesterEmail, Long bookingId) {
        Long requesterId = requester(requesterEmail).getId();
        return bookingRepository.findByIdAndRequestedById(bookingId, requesterId)
                .map(BookingService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND", "Booking was not found."));
    }

    private User requester(String requesterEmail) {
        return userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated requester does not exist."));
    }

    private static String bookingNumber() {
        return "BKG-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private static BookingResponse toResponse(Booking booking) {
        var vessel = booking.getVessel();
        var route = booking.getRoute();
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
                null,
                null,
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getCompletedAt());
    }
}
