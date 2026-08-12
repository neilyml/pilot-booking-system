package com.aiimglobal.pilot.booking.system.dashboard.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToLongFunction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.booking.persistence.BookingRepository;
import com.aiimglobal.pilot.booking.system.coupon.domain.CouponStatus;
import com.aiimglobal.pilot.booking.system.coupon.persistence.CouponRepository;
import com.aiimglobal.pilot.booking.system.dashboard.dto.AdminDashboardResponse;
import com.aiimglobal.pilot.booking.system.dashboard.dto.OwnerDashboardResponse;
import com.aiimglobal.pilot.booking.system.payment.domain.PaymentStatus;
import com.aiimglobal.pilot.booking.system.payment.persistence.CouponRedemptionRepository;
import com.aiimglobal.pilot.booking.system.pilot.domain.PilotStatus;
import com.aiimglobal.pilot.booking.system.pilot.persistence.PilotRepository;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;
import com.aiimglobal.pilot.booking.system.vessel.persistence.VesselRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VesselRepository vesselRepository;
    private final BookingRepository bookingRepository;
    private final CouponRepository couponRepository;
    private final PilotRepository pilotRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    @Transactional(readOnly = true)
    public OwnerDashboardResponse ownerDashboard(String ownerEmail) {
        Instant currentTime = Instant.now();
        BigDecimal availableValue = couponRepository.sumAvailableValue(
                ownerEmail, CouponStatus.ACTIVE, currentTime);
        return new OwnerDashboardResponse(
                counts(VesselStatus.class,
                        status -> vesselRepository.countByOwnerEmailAndStatus(ownerEmail, status)),
                counts(BookingStatus.class,
                        status -> bookingRepository.countByRequestedByEmailAndStatus(ownerEmail, status)),
                counts(CouponStatus.class,
                        status -> couponRepository.countByOwnerEmailAndStatus(ownerEmail, status)),
                couponRepository.countByOwnerEmailAndStatusAndExpiresAtAfter(
                        ownerEmail, CouponStatus.ACTIVE, currentTime),
                availableValue == null ? BigDecimal.ZERO : availableValue);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse adminDashboard() {
        BigDecimal redeemedValue = couponRedemptionRepository
                .sumAmountRedeemedByPaymentStatus(PaymentStatus.SUCCESS);
        return new AdminDashboardResponse(
                counts(VesselStatus.class, vesselRepository::countByStatus),
                counts(BookingStatus.class, bookingRepository::countByStatus),
                counts(CouponStatus.class, couponRepository::countByStatus),
                counts(PilotStatus.class, pilotRepository::countByStatus),
                redeemedValue == null ? BigDecimal.ZERO : redeemedValue);
    }

    private <E extends Enum<E>> Map<E, Long> counts(
            Class<E> statusType, ToLongFunction<E> counter) {
        var counts = new EnumMap<E, Long>(statusType);
        for (E status : statusType.getEnumConstants()) {
            counts.put(status, counter.applyAsLong(status));
        }
        return Collections.unmodifiableMap(counts);
    }
}
