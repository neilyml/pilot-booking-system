package com.aiimglobal.pilot.booking.system.pilot.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.api.PageResponse;
import com.aiimglobal.pilot.booking.system.assignment.domain.AssignmentStatus;
import com.aiimglobal.pilot.booking.system.assignment.persistence.BookingAssignmentRepository;
import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.pilot.domain.Pilot;
import com.aiimglobal.pilot.booking.system.pilot.domain.PilotStatus;
import com.aiimglobal.pilot.booking.system.pilot.dto.CreatePilotRequest;
import com.aiimglobal.pilot.booking.system.pilot.dto.PilotResponse;
import com.aiimglobal.pilot.booking.system.pilot.dto.UpdatePilotRequest;
import com.aiimglobal.pilot.booking.system.pilot.persistence.PilotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PilotService {

    private final PilotRepository pilotRepository;
    private final BookingAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public PageResponse<PilotResponse> list(PilotStatus status, Pageable pageable) {
        var pilots = status == null
                ? pilotRepository.findAll(pageable)
                : pilotRepository.findAllByStatus(status, pageable);
        return PageResponse.from(pilots.map(PilotService::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<PilotResponse> available(
            java.time.LocalDate serviceDate, Pageable pageable) {
        return PageResponse.from(pilotRepository.findAvailable(
                        serviceDate, PilotStatus.ACTIVE, AssignmentStatus.ACTIVE, pageable)
                .map(PilotService::toResponse));
    }

    @Transactional
    public PilotResponse create(String administratorEmail, CreatePilotRequest request) {
        if (pilotRepository.existsByEmployeeNumber(request.employeeNumber())) {
            throw employeeNumberConflict();
        }
        PilotResponse response = save(Pilot.create(
                request.employeeNumber(), request.name(), request.phone(), request.email()));
        log.info("action=pilot_created actor={} pilotId={}", administratorEmail, response.id());
        return response;
    }

    @Transactional
    public PilotResponse update(
            String administratorEmail, Long pilotId, UpdatePilotRequest request) {
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
            PilotResponse response = save(pilot);
            log.info("action=pilot_updated actor={} pilotId={}", administratorEmail, pilotId);
            return response;
        } catch (OptimisticLockingFailureException exception) {
            throw new ResourceConflictException(
                    "PILOT_STALE_VERSION", "The pilot profile was updated by another request.");
        }
    }

    @Transactional
    public PilotResponse deactivate(String administratorEmail, Long pilotId) {
        Pilot pilot = pilotRepository.findForUpdateById(pilotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PILOT_NOT_FOUND", "Pilot was not found."));
        if (assignmentRepository.existsByPilotIdAndStatus(pilotId, AssignmentStatus.ACTIVE)) {
            throw new ResourceConflictException(
                    "PILOT_HAS_ACTIVE_ASSIGNMENT", "A pilot with active work cannot be deactivated.");
        }
        pilot.deactivate();
        PilotResponse response = toResponse(pilotRepository.saveAndFlush(pilot));
        log.info("action=pilot_deactivated actor={} pilotId={}", administratorEmail, pilotId);
        return response;
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
