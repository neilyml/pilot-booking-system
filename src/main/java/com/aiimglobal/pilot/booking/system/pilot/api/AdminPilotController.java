package com.aiimglobal.pilot.booking.system.pilot.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiimglobal.pilot.booking.system.pilot.application.PilotService;
import com.aiimglobal.pilot.booking.system.pilot.dto.CreatePilotRequest;
import com.aiimglobal.pilot.booking.system.pilot.dto.PilotResponse;
import com.aiimglobal.pilot.booking.system.pilot.dto.UpdatePilotRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/pilots")
@RequiredArgsConstructor
public class AdminPilotController {

    private final PilotService pilotService;

    @GetMapping
    List<PilotResponse> list() {
        return pilotService.list();
    }

    @GetMapping("/available")
    List<PilotResponse> available(@RequestParam LocalDate serviceDate) {
        return pilotService.available(serviceDate);
    }

    @PostMapping
    ResponseEntity<PilotResponse> create(@Valid @RequestBody CreatePilotRequest request) {
        PilotResponse response = pilotService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/pilots/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    PilotResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePilotRequest request) {
        return pilotService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    PilotResponse deactivate(@PathVariable Long id) {
        return pilotService.deactivate(id);
    }
}
