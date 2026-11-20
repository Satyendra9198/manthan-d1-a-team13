package com.returnright.refunds;

import com.returnright.core.enums.RefundStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One refund per return [BR-11]. Amount frozen at creation (F-10). */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    private UUID id;
    private UUID returnId;
    private long customerId;
    private long amountPaise;
    @Enumerated(EnumType.STRING)
    private RefundStatus status;
    private Instant createdAt;
    private Instant completedAt;

    protected Refund() {}

    Refund(UUID returnId, long customerId, long amountPaise, Instant now) {
        this.id = UUID.randomUUID();
        this.returnId = returnId;
        this.customerId = customerId;
        this.amountPaise = amountPaise;
        this.status = RefundStatus.PENDING;
        this.createdAt = now;
    }

    void setStatus(RefundStatus s, Instant completedAt) {
        this.status = s;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public UUID getReturnId() { return returnId; }
    public long getCustomerId() { return customerId; }
    public long getAmountPaise() { return amountPaise; }
    public RefundStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
