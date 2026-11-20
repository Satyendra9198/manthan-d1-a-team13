package com.returnright.model.domain;

import com.returnright.config.SecurityConfig;
import com.returnright.model.enums.Role;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The signed-in staff member making a request, read from their token. */
public record StaffActor(UUID id, Role role) {

  public static StaffActor from(Jwt token) {
    return new StaffActor(
        UUID.fromString(token.getSubject()),
        Role.valueOf(token.getClaimAsString(SecurityConfig.ROLE_CLAIM)));
  }
}
