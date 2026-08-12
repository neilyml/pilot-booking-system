package com.aiimglobal.pilot.booking.system.booking.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aiimglobal.pilot.booking.system.booking.domain.Booking;
import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByRequestedByIdOrderById(Long requestedById);

    Optional<Booking> findByIdAndRequestedById(Long id, Long requestedById);

    List<Booking> findAllByStatusOrderById(BookingStatus status);

    long countByRequestedByEmailAndStatus(String requestedByEmail, BookingStatus status);

    long countByStatus(BookingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking from Booking booking
            where booking.id = :id and booking.requestedBy.id = :requestedById
            """)
    Optional<Booking> findForUpdateByIdAndRequestedById(
            @Param("id") Long id, @Param("requestedById") Long requestedById);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findForUpdateById(@Param("id") Long id);
}
