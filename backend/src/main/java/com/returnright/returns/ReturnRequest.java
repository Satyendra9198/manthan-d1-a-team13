package com.returnright.returns;

import com.returnright.core.enums.CancelReason;
import com.returnright.core.enums.FinanceReviewReason;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.ReturnOutcome;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.orders.Customer;
import com.returnright.orders.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Return header [A-04]. Status, version and money fields are changed ONLY by {@link ReturnStateMachine}
 * (setters are package-private; ArchitectureTest enforces it).
 */
@Entity
@Table(name = "returns")
public class ReturnRequest {

    @Id
    private UUID id;
    private String rmaNumber;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @Enumerated(EnumType.STRING)
    private ReturnStatus status;
    @Enumerated(EnumType.STRING)
    private RefundMethod refundMethod;
    private int version;
    private Long approvedRefundPaise;
    @Enumerated(EnumType.STRING)
    private ReturnOutcome outcome;
    @Column(name = "requires_finance_review")
    private boolean requiresFinanceReview;
    @Enumerated(EnumType.STRING)
    private FinanceReviewReason financeReviewReason;
    @Enumerated(EnumType.STRING)
    private CancelReason cancelReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    @OneToMany(mappedBy = "ret")
    @OrderBy("orderItemId, unitIndex")
    private List<ReturnItem> items = new ArrayList<>();

    protected ReturnRequest() {}

    ReturnRequest(String rmaNumber, Order order, Customer customer, RefundMethod method, Instant now) {
        this.id = UUID.randomUUID();
        this.rmaNumber = rmaNumber;
        this.order = order;
        this.customer = customer;
        this.status = ReturnStatus.REQUESTED;
        this.refundMethod = method;
        this.version = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.name());
        m.put("version", version);
        m.put("refund_method", refundMethod.name());
        m.put("approved_refund_paise", approvedRefundPaise);
        m.put("outcome", outcome == null ? null : outcome.name());
        m.put("requires_finance_review", requiresFinanceReview);
        m.put("finance_review_reason", financeReviewReason == null ? null : financeReviewReason.name());
        m.put("cancel_reason", cancelReason == null ? null : cancelReason.name());
        return m;
    }

    // ---- package-private mutators: state machine only ----
    void setStatus(ReturnStatus s) { this.status = s; }
    void bumpVersion(Instant now) { this.version++; this.updatedAt = now; }
    void setClosedAt(Instant at) { this.closedAt = at; }
    void setApprovedRefundPaise(Long v) { this.approvedRefundPaise = v; }
    void setOutcome(ReturnOutcome o) { this.outcome = o; }
    void setFinanceReview(boolean required, FinanceReviewReason reason) {
        this.requiresFinanceReview = required;
        this.financeReviewReason = reason;
    }
    void setCancelReason(CancelReason c) { this.cancelReason = c; }
    void setRefundMethod(RefundMethod m) { this.refundMethod = m; }

    public UUID getId() { return id; }
    public String getRmaNumber() { return rmaNumber; }
    public Order getOrder() { return order; }
    public Customer getCustomer() { return customer; }
    public ReturnStatus getStatus() { return status; }
    public RefundMethod getRefundMethod() { return refundMethod; }
    public int getVersion() { return version; }
    public Long getApprovedRefundPaise() { return approvedRefundPaise; }
    public ReturnOutcome getOutcome() { return outcome; }
    public boolean isRequiresFinanceReview() { return requiresFinanceReview; }
    public FinanceReviewReason getFinanceReviewReason() { return financeReviewReason; }
    public CancelReason getCancelReason() { return cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<ReturnItem> getItems() { return items; }
}
