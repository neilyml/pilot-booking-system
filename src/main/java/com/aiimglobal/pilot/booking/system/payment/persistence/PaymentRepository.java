package com.aiimglobal.pilot.booking.system.payment.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.payment.domain.Payment;
import com.aiimglobal.pilot.booking.system.payment.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
