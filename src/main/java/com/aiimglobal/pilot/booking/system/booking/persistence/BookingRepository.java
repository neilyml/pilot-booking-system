package com.aiimglobal.pilot.booking.system.booking.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.booking.domain.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByRequestedByIdOrderById(Long requestedById);

    Optional<Booking> findByIdAndRequestedById(Long id, Long requestedById);
}
