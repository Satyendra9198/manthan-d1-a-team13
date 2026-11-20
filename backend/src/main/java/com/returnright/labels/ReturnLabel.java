package com.returnright.labels;

import com.returnright.core.enums.LabelStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "return_labels")
public class ReturnLabel {

    @Id
    private UUID id;
    private UUID returnId;
    private String qrToken;
    private String trackingNumber;
    @Enumerated(EnumType.STRING)
    private LabelStatus status;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant usedAt;

    protected ReturnLabel() {}

    ReturnLabel(UUID returnId, String qrToken, String trackingNumber, Instant issuedAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.returnId = returnId;
        this.qrToken = qrToken;
        this.trackingNumber = trackingNumber;
        this.status = LabelStatus.ACTIVE;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    void markUsed(Instant at) {
        this.status = LabelStatus.USED;
        this.usedAt = at;
    }

    void setStatus(LabelStatus s) {
        this.status = s;
    }

    public boolean isExpiredAt(Instant now) {
        return status == LabelStatus.EXPIRED || (status == LabelStatus.ACTIVE && expiresAt.isBefore(now));
    }

    public UUID getId() { return id; }
    public UUID getReturnId() { return returnId; }
    public String getQrToken() { return qrToken; }
    public String getTrackingNumber() { return trackingNumber; }
    public LabelStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
