package com.aiimglobal.pilot.booking.system.report.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.api.PageRequests;
import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.report.application.BookingReportService;
import com.aiimglobal.pilot.booking.system.report.dto.BookingReportRow;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/reports/bookings")
@RequiredArgsConstructor
@Tag(name = "Admin reports", description = "Read-only, filterable booking reports.")
public class AdminBookingReportController {

    private final BookingReportService bookingReportService;

    @GetMapping
    @Operation(summary = "Get the booking report")
    PageResponse<BookingReportRow> report(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(name = "from", required = false) LocalDate fromDate,
            @RequestParam(name = "to", required = false) LocalDate toDate,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) Long pilotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookingReportService.report(
                status,
                fromDate,
                toDate,
                routeId,
                pilotId,
                PageRequests.newestFirst(page, size));
    }
}
