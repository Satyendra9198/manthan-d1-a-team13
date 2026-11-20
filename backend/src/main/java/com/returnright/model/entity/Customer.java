package com.returnright.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

  @Id private UUID id;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "email", nullable = false)
  private String email;

  protected Customer() {}

  public Customer(UUID id, String fullName, String email) {
    this.id = id;
    this.fullName = fullName;
    this.email = email;
  }

  public UUID getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }
}
