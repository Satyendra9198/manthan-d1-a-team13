package com.returnright.model.entity;

import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.ReturnReason;
import com.returnright.model.enums.ReturnStatus;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A customer's return. {@link #status} is only changed through {@code ReturnTransitionService}, and
 * {@link #version} provides optimistic locking against concurrent changes.
 */
@Entity
@Table(name = "return_request")
public class ReturnRequest {

  @Id private UUID id;

  @Column(name = "return_number", nullable = false)
  private String returnNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private CustomerOrder order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReturnStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "refund_method", nullable = false)
  private RefundMethod refundMethod;

  @Column(name = "flagged", nullable = false)
  private boolean flagged;

  @Column(name = "label_code")
  private String labelCode;

  @Column(name = "label_expires_at")
  private Instant labelExpiresAt;

  @Column(name = "refund_amount_paise")
  private Long refundAmountPaise;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  /**
   * Null until first persisted, which is how Spring Data tells a new return from an existing one.
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ReturnItem> items = new ArrayList<>();

  protected ReturnRequest() {}

  public ReturnRequest(
      String returnNumber,
      CustomerOrder order,
      RefundMethod refundMethod,
      boolean flagged,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.returnNumber = returnNumber;
    this.order = order;
    this.customer = order.getCustomer();
    this.refundMethod = refundMethod;
    this.flagged = flagged;
    this.status = ReturnStatus.REQUESTED;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  public ReturnItem addItem(OrderItem orderItem, int quantity, ReturnReason reason, String note) {
    ReturnItem item = new ReturnItem(this, orderItem, quantity, reason, note);
    items.add(item);
    return item;
  }

  /** Only {@code ReturnTransitionService} should call this. */
  public void moveTo(ReturnStatus newStatus, Instant at, boolean terminal) {
    this.status = newStatus;
    this.updatedAt = at;
    if (terminal) {
      this.closedAt = at;
    }
  }

  public void issueLabel(String labelCode, Instant expiresAt) {
    this.labelCode = labelCode;
    this.labelExpiresAt = expiresAt;
  }

  public void setRefundAmountPaise(long refundAmountPaise) {
    this.refundAmountPaise = refundAmountPaise;
  }

  public UUID getId() {
    return id;
  }

  public String getReturnNumber() {
    return returnNumber;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public Customer getCustomer() {
    return customer;
  }

  public ReturnStatus getStatus() {
    return status;
  }

  public RefundMethod getRefundMethod() {
    return refundMethod;
  }

  public boolean isFlagged() {
    return flagged;
  }

  public String getLabelCode() {
    return labelCode;
  }

  public Instant getLabelExpiresAt() {
    return labelExpiresAt;
  }

  public Long getRefundAmountPaise() {
    return refundAmountPaise;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public List<ReturnItem> getItems() {
    return items;
  }
}
