package com.aiimglobal.pilot.booking.system.route.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.route.domain.Route;

public interface RouteRepository extends JpaRepository<Route, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Route> findAllByActiveTrueOrderById();

    Optional<Route> findByIdAndActiveTrue(Long id);
}
