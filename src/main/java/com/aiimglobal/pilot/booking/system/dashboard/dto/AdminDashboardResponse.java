package com.aiimglobal.pilot.booking.system.dashboard.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.coupon.domain.CouponStatus;
import com.aiimglobal.pilot.booking.system.pilot.domain.PilotStatus;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;

public record AdminDashboardResponse(
        Map<VesselStatus, Long> vesselCounts,
        Map<BookingStatus, Long> bookingCounts,
        Map<CouponStatus, Long> couponCounts,
        Map<PilotStatus, Long> pilotCounts,
        BigDecimal redeemedValue) {
}
