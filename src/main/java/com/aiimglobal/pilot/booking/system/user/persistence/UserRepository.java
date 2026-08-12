package com.aiimglobal.pilot.booking.system.user.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aiimglobal.pilot.booking.system.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByEmail(String email);

    @Query("select distinct user from User user left join fetch user.roles where user.email = :email")
    Optional<User> findWithRolesByEmail(@Param("email") String email);
}
