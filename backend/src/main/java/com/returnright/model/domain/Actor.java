package com.returnright.model.domain;

import com.returnright.model.enums.Role;

/**
 * Who is performing an action, recorded in the audit log.
 *
 * @param id staff username, customer email, or {@code system}
 */
public record Actor(Role role, String id) {

  public static Actor system() {
    return new Actor(Role.SYSTEM, "system");
  }

  public static Actor customer(String email) {
    return new Actor(Role.CUSTOMER, email);
  }
}
