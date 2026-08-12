package com.aiimglobal.pilot.booking.system.user.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aiimglobal.pilot.booking.system.user.domain.Role;
import com.aiimglobal.pilot.booking.system.user.domain.RoleName;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
