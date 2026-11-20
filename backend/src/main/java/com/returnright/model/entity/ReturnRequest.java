package com.returnright.model.entity;

import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * A customer's return (RMA). Status lives here, not on the items (assumption A-03). The status is
 * only ever changed with a target state the state machine has produced, and every change is kept in
 * the status history.
 */
@Entity
@Table(name = "return_request")
public class ReturnRequest {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "rma_number", nullable = false, unique = true, updatable = false)
  private String rmaNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private CustomerOrder order;

  @Column(name = "customer_email", nullable = false, updatable = false)
  private String customerEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReturnStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "refund_method", nullable = false, updatable = false)
  private RefundMethod refundMethod;

  @Column(name = "flagged_for_review", nullable = false, updatable = false)
  private boolean flaggedForReview;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "inspected_at")
  private Instant inspectedAt;

  @Column(name = "inspected_by")
  private UUID inspectedBy;

  /** Grade-adjusted refund decided at inspection (A-06); {@code null} before inspection. */
  @Column(name = "refund_amount_paise")
  private Long refundAmountPaise;

  @Enumerated(EnumType.STRING)
  @Column(name = "rejection_reason")
  private RejectionReason rejectionReason;

  @Column(name = "rejection_note")
  private String rejectionNote;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ReturnItem> items = new ArrayList<>();

  @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("changedAt ASC")
  @BatchSize(size = 50)
  private List<ReturnStatusChange> statusHistory = new ArrayList<>();

  protected ReturnRequest() {}

  /** A new return in {@code REQUESTED}, created by the customer. */
  public ReturnRequest(
      UUID id,
      String rmaNumber,
      CustomerOrder order,
      RefundMethod refundMethod,
      boolean flaggedForReview,
      Instant createdAt) {
    this.id = id;
    this.rmaNumber = rmaNumber;
    this.order = order;
    this.customerEmail = order.getCustomerEmail();
    this.status = ReturnStatus.REQUESTED;
    this.refundMethod = refundMethod;
    this.flaggedForReview = flaggedForReview;
    this.createdAt = createdAt;
    statusHistory.add(
        new ReturnStatusChange(this, ReturnStatus.REQUESTED, createdAt, Role.CUSTOMER, null));
  }

  /**
   * Moves to a state the state machine has already validated and records it in the history.
   *
   * @param actorId the staff member, or {@code null} for the customer and the system
   */
  public void moveTo(ReturnStatus next, Instant at, Role actorRole, UUID actorId) {
    this.status = next;
    if (next == ReturnStatus.APPROVED) {
      this.approvedAt = at;
    }
    statusHistory.add(new ReturnStatusChange(this, next, at, actorRole, actorId));
  }

  /** Records the warehouse's verdict; the refund amount is 0 when nothing was accepted. */
  public void recordInspection(UUID agentId, Instant at, long refundAmountPaise) {
    this.inspectedBy = agentId;
    this.inspectedAt = at;
    this.refundAmountPaise = refundAmountPaise;
  }

  public void recordRejection(RejectionReason reason, String note) {
    this.rejectionReason = reason;
    this.rejectionNote = note;
  }

  public void addItem(ReturnItem item) {
    item.attachTo(this);
    items.add(item);
  }

  public UUID getId() {
    return id;
  }

  public String getRmaNumber() {
    return rmaNumber;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public ReturnStatus getStatus() {
    return status;
  }

  public RefundMethod getRefundMethod() {
    return refundMethod;
  }

  public boolean isFlaggedForReview() {
    return flaggedForReview;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public Instant getInspectedAt() {
    return inspectedAt;
  }

  public UUID getInspectedBy() {
    return inspectedBy;
  }

  public Long getRefundAmountPaise() {
    return refundAmountPaise;
  }

  public RejectionReason getRejectionReason() {
    return rejectionReason;
  }

  public String getRejectionNote() {
    return rejectionNote;
  }

  public List<ReturnItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  public List<ReturnStatusChange> getStatusHistory() {
    return Collections.unmodifiableList(statusHistory);
  }
}
