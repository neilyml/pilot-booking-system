package com.aiimglobal.pilot.booking.system.route.application;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.route.domain.Route;
import com.aiimglobal.pilot.booking.system.route.dto.RouteRequest;
import com.aiimglobal.pilot.booking.system.route.dto.RouteResponse;
import com.aiimglobal.pilot.booking.system.route.persistence.RouteRepository;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final UserRepository userRepository;

    @Transactional
    public RouteResponse create(String creatorEmail, RouteRequest request) {
        if (routeRepository.existsByCode(request.code())) {
            throw codeConflict();
        }
        var creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated creator does not exist."));
        var route = Route.create(
                request.code(),
                request.name(),
                request.origin(),
                request.destination(),
                request.serviceFee(),
                creator);
        return save(route);
    }

    @Transactional
    public RouteResponse update(Long routeId, RouteRequest request) {
        if (routeRepository.existsByCodeAndIdNot(request.code(), routeId)) {
            throw codeConflict();
        }
        var route = route(routeId);
        route.update(
                request.code(),
                request.name(),
                request.origin(),
                request.destination(),
                request.serviceFee());
        return save(route);
    }

    @Transactional
    public RouteResponse activate(Long routeId) {
        var route = route(routeId);
        route.activate();
        return toResponse(routeRepository.saveAndFlush(route));
    }

    @Transactional
    public RouteResponse deactivate(Long routeId) {
        var route = route(routeId);
        route.deactivate();
        return toResponse(routeRepository.saveAndFlush(route));
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> listActive() {
        return routeRepository.findAllByActiveTrueOrderById().stream()
                .map(RouteService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse getActive(Long routeId) {
        return routeRepository.findByIdAndActiveTrue(routeId)
                .map(RouteService::toResponse)
                .orElseThrow(RouteService::notFound);
    }

    private Route route(Long routeId) {
        return routeRepository.findById(routeId).orElseThrow(RouteService::notFound);
    }

    private RouteResponse save(Route route) {
        try {
            return toResponse(routeRepository.saveAndFlush(route));
        } catch (DataIntegrityViolationException exception) {
            throw codeConflict();
        }
    }

    private static ResourceConflictException codeConflict() {
        return new ResourceConflictException("ROUTE_CODE_EXISTS", "The route code is already in use.");
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("ROUTE_NOT_FOUND", "Route was not found.");
    }

    private static RouteResponse toResponse(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getCode(),
                route.getName(),
                route.getOrigin(),
                route.getDestination(),
                route.getServiceFee(),
                route.isActive(),
                route.getCreatedBy().getId(),
                route.getCreatedBy().getEmail(),
                route.getCreatedAt(),
                route.getUpdatedAt());
    }
}
