package com.aiimglobal.pilot.booking.system.vessel.application;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiimglobal.pilot.booking.system.exception.ResourceConflictException;
import com.aiimglobal.pilot.booking.system.exception.ResourceNotFoundException;
import com.aiimglobal.pilot.booking.system.user.persistence.UserRepository;
import com.aiimglobal.pilot.booking.system.vessel.domain.Vessel;
import com.aiimglobal.pilot.booking.system.vessel.dto.RegisterVesselRequest;
import com.aiimglobal.pilot.booking.system.vessel.dto.VesselResponse;
import com.aiimglobal.pilot.booking.system.vessel.persistence.VesselRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VesselService {

    private final VesselRepository vesselRepository;
    private final UserRepository userRepository;

    @Transactional
    public VesselResponse register(String authenticatedEmail, RegisterVesselRequest request) {
        if (vesselRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw registrationConflict();
        }
        var owner = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist."));
        try {
            Vessel vessel = Vessel.register(
                    owner,
                    request.name(),
                    request.registrationNumber(),
                    request.vesselType());
            return toResponse(vesselRepository.saveAndFlush(vessel));
        } catch (DataIntegrityViolationException exception) {
            throw registrationConflict();
        }
    }

    @Transactional(readOnly = true)
    public List<VesselResponse> list(String authenticatedEmail) {
        Long ownerId = ownerId(authenticatedEmail);
        return vesselRepository.findAllByOwnerIdOrderById(ownerId).stream()
                .map(VesselService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VesselResponse get(String authenticatedEmail, Long vesselId) {
        Long ownerId = ownerId(authenticatedEmail);
        return vesselRepository.findByIdAndOwnerId(vesselId, ownerId)
                .map(VesselService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("VESSEL_NOT_FOUND", "Vessel was not found."));
    }

    private Long ownerId(String authenticatedEmail) {
        return userRepository.findByEmail(authenticatedEmail)
                .map(user -> user.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist."));
    }

    private static ResourceConflictException registrationConflict() {
        return new ResourceConflictException(
                "VESSEL_REGISTRATION_EXISTS", "The vessel registration number is already registered.");
    }

    private static VesselResponse toResponse(Vessel vessel) {
        return new VesselResponse(
                vessel.getId(),
                vessel.getOwner().getId(),
                vessel.getName(),
                vessel.getRegistrationNumber(),
                vessel.getVesselType(),
                vessel.getStatus(),
                vessel.getCreatedAt(),
                vessel.getUpdatedAt());
    }
}
