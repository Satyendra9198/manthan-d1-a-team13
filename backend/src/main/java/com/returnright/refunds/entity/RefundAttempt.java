package com.returnright.refunds.entity;

import com.returnright.core.enums.AttemptStatus;
import com.returnright.core.enums.RefundMethod;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One execution of a refund with its own gateway idempotency key [BR-14][BR-15]. */
@Entity
@Table(name = "refund_attempts")
public class RefundAttempt {

    @Id
    private UUID id;
    private UUID refundId;
    private Short attemptNo;
    @Enumerated(EnumType.STRING)
    private RefundMethod method;
    private String gatewayIdempotencyKey;
    @Enumerated(EnumType.STRING)
    private AttemptStatus status;
    private String gatewayRefundId;
    private String declineCode;
    private Instant createdAt;
    private Instant requestSentAt;
    private Instant lastCheckedAt;
    private int checkCount;
    private Instant completedAt;

    protected RefundAttempt() {}

    public RefundAttempt(UUID refundId, int attemptNo, RefundMethod method, Instant now) {
        this.id = UUID.randomUUID();
        this.refundId = refundId;
        this.attemptNo = (short) attemptNo;
        this.method = method;
        this.gatewayIdempotencyKey = "rr-" + refundId + "-" + attemptNo; // deterministic, persisted before the call
        this.status = AttemptStatus.PENDING;
        this.createdAt = now;
    }

    public boolean isTerminal() {
        return status == AttemptStatus.SUCCEEDED || status == AttemptStatus.FAILED;
    }

    public void markSent(Instant at) { this.requestSentAt = at; }
    public void markChecked(Instant at) { this.lastCheckedAt = at; this.checkCount++; }
    public void succeed(String gatewayRefundId, Instant at) {
        this.status = AttemptStatus.SUCCEEDED;
        this.gatewayRefundId = gatewayRefundId;
        this.completedAt = at;
    }
    public void fail(String declineCode, Instant at) {
        this.status = AttemptStatus.FAILED;
        this.declineCode = declineCode;
        this.completedAt = at;
    }
    public void unknown() { this.status = AttemptStatus.UNKNOWN; }

    public UUID getId() { return id; }
    public UUID getRefundId() { return refundId; }
    public int getAttemptNo() { return attemptNo; }
    public RefundMethod getMethod() { return method; }
    public String getGatewayIdempotencyKey() { return gatewayIdempotencyKey; }
    public AttemptStatus getStatus() { return status; }
    public String getGatewayRefundId() { return gatewayRefundId; }
    public String getDeclineCode() { return declineCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRequestSentAt() { return requestSentAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public int getCheckCount() { return checkCount; }
    public Instant getCompletedAt() { return completedAt; }
}
