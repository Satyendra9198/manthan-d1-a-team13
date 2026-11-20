package com.returnright.refunds;

import java.util.UUID;

/** One refund per return. The idempotency key is the one sent to the gateway on every attempt. */
public class Refund {

  private final UUID id;
  private final UUID returnId;
  private final long amountPaise;
  private final RefundMethod method;
  private final String idempotencyKey;
  private RefundStatus status;
  private String gatewayTransactionId;
  private String failureReason;

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
