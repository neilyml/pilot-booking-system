package com.aiimglobal.pilot.booking.system.vessel.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.vessel.domain.Vessel;
import com.aiimglobal.pilot.booking.system.vessel.domain.VesselStatus;

public interface VesselRepository extends JpaRepository<Vessel, Long> {

    boolean existsByRegistrationNumber(String registrationNumber);

    List<Vessel> findAllByOwnerIdOrderById(Long ownerId);

    Optional<Vessel> findByIdAndOwnerId(Long id, Long ownerId);

    List<Vessel> findAllByStatusOrderById(VesselStatus status);
}
