package com.aiimglobal.pilot.booking.system.report.persistence;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.aiimglobal.pilot.booking.system.booking.domain.BookingStatus;
import com.aiimglobal.pilot.booking.system.report.dto.BookingReportRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BookingReportRepository {

    private static final String ROW_QUERY = """
            select new com.aiimglobal.pilot.booking.system.report.dto.BookingReportRow(
                booking.bookingNumber,
                owner.fullName,
                vessel.name,
                vessel.registrationNumber,
                route.code,
                route.name,
                booking.serviceDate,
                booking.serviceFee,
                booking.status,
                payment.status,
                pilot.name
            )
            from Booking booking
            join booking.requestedBy owner
            join booking.vessel vessel
            join booking.route route
            left join Payment payment
                on payment.booking = booking
                and payment.status = com.aiimglobal.pilot.booking.system.payment.domain.PaymentStatus.SUCCESS
            left join BookingAssignment assignment
                on assignment.id = (
                    select max(candidate.id)
                    from BookingAssignment candidate
                    where candidate.booking = booking
                )
            left join assignment.pilot pilot
            where 1 = 1
            """;

    private static final String COUNT_QUERY = """
            select count(booking)
            from Booking booking
            join booking.route route
            left join BookingAssignment assignment
                on assignment.id = (
                    select max(candidate.id)
                    from BookingAssignment candidate
                    where candidate.booking = booking
                )
            left join assignment.pilot pilot
            where 1 = 1
            """;

    private final EntityManager entityManager;

    public Page<BookingReportRow> findBookings(
            BookingStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Long routeId,
            Long pilotId,
            Pageable pageable) {
        String filters = filters(status, fromDate, toDate, routeId, pilotId);
        TypedQuery<BookingReportRow> rowQuery = entityManager.createQuery(
                ROW_QUERY + filters + " order by booking.createdAt desc, booking.id desc",
                BookingReportRow.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                COUNT_QUERY + filters, Long.class);
        bind(rowQuery, status, fromDate, toDate, routeId, pilotId);
        bind(countQuery, status, fromDate, toDate, routeId, pilotId);
        rowQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));
        rowQuery.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(rowQuery.getResultList(), pageable, countQuery.getSingleResult());
    }

    private String filters(
            BookingStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Long routeId,
            Long pilotId) {
        var filters = new StringBuilder();
        if (status != null) {
            filters.append(" and booking.status = :status");
        }
        if (fromDate != null) {
            filters.append(" and booking.serviceDate >= :fromDate");
        }
        if (toDate != null) {
            filters.append(" and booking.serviceDate <= :toDate");
        }
        if (routeId != null) {
            filters.append(" and route.id = :routeId");
        }
        if (pilotId != null) {
            filters.append(" and pilot.id = :pilotId");
        }
        return filters.toString();
    }

    private void bind(
            TypedQuery<?> query,
            BookingStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Long routeId,
            Long pilotId) {
        if (status != null) {
            query.setParameter("status", status);
        }
        if (fromDate != null) {
            query.setParameter("fromDate", fromDate);
        }
        if (toDate != null) {
            query.setParameter("toDate", toDate);
        }
        if (routeId != null) {
            query.setParameter("routeId", routeId);
        }
        if (pilotId != null) {
            query.setParameter("pilotId", pilotId);
        }
    }
}
