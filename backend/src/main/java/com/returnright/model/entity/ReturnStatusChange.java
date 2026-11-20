package com.returnright.model.entity;

import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One entry in a return's status history: the timeline and the audit trail. Never updated. */
@Entity
@Table(name = "return_status_change")
public class ReturnStatusChange {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "return_id", nullable = false, updatable = false)
  private ReturnRequest returnRequest;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, updatable = false)
  private ReturnStatus status;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_role", nullable = false, updatable = false)
  private Role actorRole;

  /** The staff member, or {@code null} for the customer and the system. */
  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  protected ReturnStatusChange() {}

  ReturnStatusChange(
      ReturnRequest returnRequest,
      ReturnStatus status,
      Instant changedAt,
      Role actorRole,
      UUID actorId) {
    this.id = UUID.randomUUID();
    this.returnRequest = returnRequest;
    this.status = status;
    this.changedAt = changedAt;
    this.actorRole = actorRole;
    this.actorId = actorId;
  }

  public UUID getId() {
    return id;
  }

  public ReturnStatus getStatus() {
    return status;
  }

  public Instant getChangedAt() {
    return changedAt;
  }

  public Role getActorRole() {
    return actorRole;
  }

  public UUID getActorId() {
    return actorId;
  }
}
