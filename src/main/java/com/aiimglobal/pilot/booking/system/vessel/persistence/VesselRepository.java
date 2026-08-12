package com.aiimglobal.pilot.booking.system.vessel.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.vessel.domain.Vessel;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;

public interface VesselRepository extends JpaRepository<Vessel, Long> {

    boolean existsByRegistrationNumber(String registrationNumber);

    Page<Vessel> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<Vessel> findAllByOwnerIdAndStatus(
            Long ownerId, VesselStatus status, Pageable pageable);

    Optional<Vessel> findByIdAndOwnerId(Long id, Long ownerId);

    Page<Vessel> findAllByStatus(VesselStatus status, Pageable pageable);

    long countByOwnerEmailAndStatus(String ownerEmail, VesselStatus status);

    long countByStatus(VesselStatus status);
}
