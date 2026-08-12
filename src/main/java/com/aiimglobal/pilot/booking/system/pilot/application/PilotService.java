package com.aiimglobal.pilot.booking.system.pilot.application;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.pilot.domain.Pilot;
import com.aiimglobal.pilot.booking.system.pilot.dto.CreatePilotRequest;
import com.aiimglobal.pilot.booking.system.pilot.dto.PilotResponse;
import com.aiimglobal.pilot.booking.system.pilot.dto.UpdatePilotRequest;
import com.aiimglobal.pilot.booking.system.pilot.persistence.PilotRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PilotService {

    private final PilotRepository pilotRepository;

    @Transactional(readOnly = true)
    public List<PilotResponse> list() {
        return pilotRepository.findAllByOrderById().stream()
                .map(PilotService::toResponse)
                .toList();
    }

    @Transactional
    public PilotResponse create(CreatePilotRequest request) {
        if (pilotRepository.existsByEmployeeNumber(request.employeeNumber())) {
            throw employeeNumberConflict();
        }
        return save(Pilot.create(
                request.employeeNumber(), request.name(), request.phone(), request.email()));
    }

    @Transactional
    public PilotResponse update(Long pilotId, UpdatePilotRequest request) {
        if (pilotRepository.existsByEmployeeNumberAndIdNot(request.employeeNumber(), pilotId)) {
            throw employeeNumberConflict();
        }
        Pilot pilot = pilot(pilotId);
        if (pilot.getVersion() != request.version()) {
            throw new ResourceConflictException(
                    "PILOT_STALE_VERSION", "The pilot profile was updated by another request.");
        }
        pilot.updateProfile(
                request.employeeNumber(), request.name(), request.phone(), request.email());
        try {
            return save(pilot);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResourceConflictException(
                    "PILOT_STALE_VERSION", "The pilot profile was updated by another request.");
        }
    }

    @Transactional
    public PilotResponse deactivate(Long pilotId) {
        Pilot pilot = pilot(pilotId);
        pilot.deactivate();
        return toResponse(pilotRepository.saveAndFlush(pilot));
    }

    private Pilot pilot(Long pilotId) {
        return pilotRepository.findById(pilotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PILOT_NOT_FOUND", "Pilot was not found."));
    }

    private PilotResponse save(Pilot pilot) {
        try {
            return toResponse(pilotRepository.saveAndFlush(pilot));
        } catch (DataIntegrityViolationException exception) {
            throw employeeNumberConflict();
        }
    }

    private static ResourceConflictException employeeNumberConflict() {
        return new ResourceConflictException(
                "PILOT_EMPLOYEE_NUMBER_EXISTS", "The employee number is already in use.");
    }

    private static PilotResponse toResponse(Pilot pilot) {
        return new PilotResponse(
                pilot.getId(),
                pilot.getEmployeeNumber(),
                pilot.getName(),
                pilot.getPhone(),
                pilot.getEmail(),
                pilot.getStatus(),
                pilot.getCreatedAt(),
                pilot.getUpdatedAt(),
                pilot.getVersion());
    }
}
