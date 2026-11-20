package com.returnright.repository;

import com.returnright.model.entity.StaffUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Staff accounts. Emails are stored lower-case. */
public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

  Optional<StaffUser> findByEmail(String email);
}
