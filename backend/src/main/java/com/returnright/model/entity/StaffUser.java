package com.returnright.model.entity;

import com.returnright.model.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A staff member who signs in: warehouse agent, finance reviewer or operations manager. */
@Entity
@Table(name = "staff_user")
public class StaffUser {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private Role role;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected StaffUser() {}

  public StaffUser(
      UUID id, String email, String fullName, Role role, String passwordHash, boolean active) {
    this.id = id;
    this.email = email;
    this.fullName = fullName;
    this.role = role;
    this.passwordHash = passwordHash;
    this.active = active;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getFullName() {
    return fullName;
  }

  public Role getRole() {
    return role;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isActive() {
    return active;
  }
}
