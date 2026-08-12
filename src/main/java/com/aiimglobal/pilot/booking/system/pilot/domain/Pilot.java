package com.aiimglobal.pilot.booking.system.pilot.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pilots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pilot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_number", nullable = false, unique = true, length = 80)
    private String employeeNumber;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PilotStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private Pilot(String employeeNumber, String name, String phone, String email) {
        this.employeeNumber = employeeNumber;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.status = PilotStatus.ACTIVE;
    }

    public static Pilot create(String employeeNumber, String name, String phone, String email) {
        return new Pilot(employeeNumber, name, phone, email);
    }

    public void updateProfile(String employeeNumber, String name, String phone, String email) {
        this.employeeNumber = employeeNumber;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public void deactivate() {
        status = PilotStatus.INACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
