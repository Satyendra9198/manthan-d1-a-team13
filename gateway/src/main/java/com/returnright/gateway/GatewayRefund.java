package com.returnright.gateway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** A refund as the gateway sees it. The idempotency key is the primary key: one payout per key, ever. */
@Entity
@Table(name = "gateway_refunds", indexes = @Index(name = "ix_gw_created", columnList = "createdAt"))
public class GatewayRefund {

    public interface Repo extends JpaRepository<GatewayRefund, String> {
        Optional<GatewayRefund> findByIdempotencyKey(String key);

        @Query("select r from GatewayRefund r where r.createdAt >= ?1 order by r.createdAt")
        List<GatewayRefund> findSince(Instant since, Pageable page);

        @Query("select r from GatewayRefund r order by r.createdAt desc")
        List<GatewayRefund> latest(Pageable page);
    }

    @Id
    @Column(length = 128)
    private String idempotencyKey;
    @Column(length = 64, nullable = false)
    private String requestHash;
    @Column(unique = true, nullable = false)
    private String gatewayRefundId;
    private String paymentReference;
    private long amountPaise;
    private String currency;
    private String merchantReference;
    private String status; // SUCCEEDED | FAILED | PROCESSING
    private String declineCode;
    private Instant createdAt;
    private Instant settledAt;
    private Instant settleAfter;

    protected GatewayRefund() {}

    GatewayRefund(String key, String hash, String gatewayRefundId, String paymentReference, long amountPaise,
                  String currency, String merchantReference, String status, String declineCode, Instant now,
                  Instant settleAfter) {
        this.idempotencyKey = key;
        this.requestHash = hash;
        this.gatewayRefundId = gatewayRefundId;
        this.paymentReference = paymentReference;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.merchantReference = merchantReference;
        this.status = status;
        this.declineCode = declineCode;
        this.createdAt = now;
        this.settledAt = "PROCESSING".equals(status) ? null : now;
        this.settleAfter = settleAfter;
    }

    /** PROCESSING refunds settle to SUCCEEDED after a delay (exercises reconciler polling). */
    boolean settleIfDue(Instant now) {
        if ("PROCESSING".equals(status) && settleAfter != null && !now.isBefore(settleAfter)) {
            status = "SUCCEEDED";
            settledAt = now;
            return true;
        }
        return false;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getGatewayRefundId() { return gatewayRefundId; }
    public String getPaymentReference() { return paymentReference; }
    public long getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public String getMerchantReference() { return merchantReference; }
    public String getStatus() { return status; }
    public String getDeclineCode() { return declineCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSettledAt() { return settledAt; }
}
