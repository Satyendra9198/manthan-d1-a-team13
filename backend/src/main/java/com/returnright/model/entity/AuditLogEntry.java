package com.returnright.model.entity;

import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** One state change of a return. Rows are never updated or deleted (enforced by a DB trigger). */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "return_id", nullable = false)
  private UUID returnId;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_role", nullable = false)
  private Role actorRole;

  @Column(name = "actor_id", nullable = false)
  private String actorId;

  @Column(name = "event", nullable = false)
  private String event;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status")
  private ReturnStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false)
  private ReturnStatus toStatus;

  @Column(name = "details")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditLogEntry() {}

  public AuditLogEntry(
      UUID returnId,
      Role actorRole,
      String actorId,
      String event,
      ReturnStatus fromStatus,
      ReturnStatus toStatus,
      String details,
      Instant createdAt) {
    this.returnId = returnId;
    this.actorRole = actorRole;
    this.actorId = actorId;
    this.event = event;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.details = details;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public UUID getReturnId() {
    return returnId;
  }

  public Role getActorRole() {
    return actorRole;
  }

  public String getActorId() {
    return actorId;
  }

  public String getEvent() {
    return event;
  }

  public ReturnStatus getFromStatus() {
    return fromStatus;
  }

  public ReturnStatus getToStatus() {
    return toStatus;
  }

  public String getDetails() {
    return details;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
