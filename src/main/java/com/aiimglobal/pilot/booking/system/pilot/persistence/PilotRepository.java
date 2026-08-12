package com.aiimglobal.pilot.booking.system.pilot.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.pilot.domain.Pilot;

public interface PilotRepository extends JpaRepository<Pilot, Long> {

    boolean existsByEmployeeNumber(String employeeNumber);

    boolean existsByEmployeeNumberAndIdNot(String employeeNumber, Long id);

    List<Pilot> findAllByOrderById();
}
