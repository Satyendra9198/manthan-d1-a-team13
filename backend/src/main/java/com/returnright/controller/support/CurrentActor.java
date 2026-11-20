package com.returnright.controller.support;

import com.returnright.model.domain.Actor;
import com.returnright.model.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Turns the authenticated staff user into an {@link Actor} for the audit log. */
public final class CurrentActor {

  private static final String ROLE_PREFIX = "ROLE_";

  private CurrentActor() {}

  public static Actor from(Authentication authentication) {
    Role role =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith(ROLE_PREFIX))
            .map(authority -> Role.valueOf(authority.substring(ROLE_PREFIX.length())))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
    return new Actor(role, authentication.getName());
  }
}
