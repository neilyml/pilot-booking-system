package com.aiimglobal.pilot.booking.system.pilot.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aiimglobal.pilot.booking.system.assignment.domain.AssignmentStatus;
import com.aiimglobal.pilot.booking.system.pilot.domain.Pilot;
import com.aiimglobal.pilot.booking.system.pilot.domain.PilotStatus;

public interface PilotRepository extends JpaRepository<Pilot, Long> {

    boolean existsByEmployeeNumber(String employeeNumber);

    boolean existsByEmployeeNumberAndIdNot(String employeeNumber, Long id);

    Page<Pilot> findAllByStatus(PilotStatus status, Pageable pageable);

    long countByStatus(PilotStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pilot from Pilot pilot where pilot.id = :id")
    Optional<Pilot> findForUpdateById(@Param("id") Long id);

    @Query("""
            select pilot from Pilot pilot
            where pilot.status = :pilotStatus
              and not exists (
                  select assignment.id from BookingAssignment assignment
                  where assignment.pilot = pilot
                    and assignment.serviceDate = :serviceDate
                    and assignment.status = :assignmentStatus
              )
            """)
    Page<Pilot> findAvailable(
            @Param("serviceDate") java.time.LocalDate serviceDate,
            @Param("pilotStatus") PilotStatus pilotStatus,
            @Param("assignmentStatus") AssignmentStatus assignmentStatus,
            Pageable pageable);
}
