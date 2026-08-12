package com.aiimglobal.pilot.booking.system.report.application;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.exception.InvalidRequestParameterException;
import com.aiimglobal.pilot.booking.system.report.dto.BookingReportRow;
import com.aiimglobal.pilot.booking.system.report.persistence.BookingReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingReportService {

    private final BookingReportRepository bookingReportRepository;

    @Transactional(readOnly = true)
    public PageResponse<BookingReportRow> report(
            BookingStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Long routeId,
            Long pilotId,
            Pageable pageable) {
        validateFilters(fromDate, toDate, routeId, pilotId);
        return PageResponse.from(bookingReportRepository.findBookings(
                status, fromDate, toDate, routeId, pilotId, pageable));
    }

    private void validateFilters(
            LocalDate fromDate, LocalDate toDate, Long routeId, Long pilotId) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidRequestParameterException(
                    "From service date must not be after to service date.");
        }
        if (routeId != null && routeId < 1) {
            throw new InvalidRequestParameterException("Route id must be positive.");
        }
        if (pilotId != null && pilotId < 1) {
            throw new InvalidRequestParameterException("Pilot id must be positive.");
        }
    }
}
