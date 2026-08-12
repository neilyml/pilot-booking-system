package com.aiimglobal.pilot.booking.system.route.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.route.domain.Route;

public interface RouteRepository extends JpaRepository<Route, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    Page<Route> findAllByActiveTrue(Pageable pageable);

    Optional<Route> findByIdAndActiveTrue(Long id);
}
