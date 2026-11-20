package com.returnright.model.entity;

import com.returnright.model.domain.IssueRefundCommand;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * One refund per return. The idempotency key is the one sent to the gateway on every attempt. The
 * unique constraints are the database-level guard against paying twice (ADR-001).
 */
@Entity
@Table(
    name = "refund",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_refund_return_id", columnNames = "return_id"),
      @UniqueConstraint(name = "uq_refund_idempotency_key", columnNames = "idempotency_key")
    })
public class Refund {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "return_id", nullable = false, updatable = false)
  private UUID returnId;

  @Column(name = "amount_paise", nullable = false, updatable = false)
  private long amountPaise;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, updatable = false)
  private RefundMethod method;

  @Column(name = "idempotency_key", nullable = false, updatable = false)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private RefundStatus status;

  @Column(name = "gateway_transaction_id")
  private String gatewayTransactionId;

  @Column(name = "failure_reason")
  private String failureReason;

  protected Refund() {}

  public Refund(
      UUID id,
      UUID returnId,
      long amountPaise,
      RefundMethod method,
      String idempotencyKey,
      RefundStatus status,
      String gatewayTransactionId,
      String failureReason) {
    this.id = id;
    this.returnId = returnId;
    this.amountPaise = amountPaise;
    this.method = method;
    this.idempotencyKey = idempotencyKey;
    this.status = status;
    this.gatewayTransactionId = gatewayTransactionId;
    this.failureReason = failureReason;
  }

  public static Refund pending(IssueRefundCommand command) {
    return new Refund(
        UUID.randomUUID(),
        command.returnId(),
        command.amountPaise(),
        command.method(),
        command.idempotencyKey(),
        RefundStatus.PENDING,
        null,
        null);
  }

  public void markSucceeded(String gatewayTransactionId) {
    this.status = RefundStatus.SUCCEEDED;
    this.gatewayTransactionId = gatewayTransactionId;
    this.failureReason = null;
  }

  public void markFailed(String failureReason) {
    this.status = RefundStatus.FAILED;
    this.failureReason = failureReason;
  }

  public void markUnknown() {
    this.status = RefundStatus.UNKNOWN;
  }

  public UUID getId() {
    return id;
  }

  public UUID getReturnId() {
    return returnId;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public RefundMethod getMethod() {
    return method;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public RefundStatus getStatus() {
    return status;
  }

  public String getGatewayTransactionId() {
    return gatewayTransactionId;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
