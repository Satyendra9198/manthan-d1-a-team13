package com.returnright.model.entity;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.ReturnReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** One order line inside a return, with the warehouse's inspection result once inspected. */
@Entity
@Table(name = "return_item")
public class ReturnItem {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "return_id", nullable = false)
  private ReturnRequest returnRequest;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_item_id", nullable = false)
  private OrderItem orderItem;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false)
  private ReturnReason reason;

  @Column(name = "reason_note")
  private String reasonNote;

  @Column(name = "accepted_quantity")
  private Integer acceptedQuantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "grade")
  private InspectionGrade grade;

  @Column(name = "inspection_notes")
  private String inspectionNotes;

  @Column(name = "line_refund_paise")
  private Long lineRefundPaise;

  protected ReturnItem() {}

  public ReturnItem(
      ReturnRequest returnRequest,
      OrderItem orderItem,
      int quantity,
      ReturnReason reason,
      String reasonNote) {
    this.id = UUID.randomUUID();
    this.returnRequest = returnRequest;
    this.orderItem = orderItem;
    this.quantity = quantity;
    this.reason = reason;
    this.reasonNote = reasonNote;
  }

  public void recordInspection(
      int acceptedQuantity, InspectionGrade grade, String inspectionNotes, long lineRefundPaise) {
    this.acceptedQuantity = acceptedQuantity;
    this.grade = grade;
    this.inspectionNotes = inspectionNotes;
    this.lineRefundPaise = lineRefundPaise;
  }

  public UUID getId() {
    return id;
  }

  public ReturnRequest getReturnRequest() {
    return returnRequest;
  }

  public OrderItem getOrderItem() {
    return orderItem;
  }

  public int getQuantity() {
    return quantity;
  }

  public ReturnReason getReason() {
    return reason;
  }

  public String getReasonNote() {
    return reasonNote;
  }

  public Integer getAcceptedQuantity() {
    return acceptedQuantity;
  }

  public InspectionGrade getGrade() {
    return grade;
  }

  public String getInspectionNotes() {
    return inspectionNotes;
  }

  public Long getLineRefundPaise() {
    return lineRefundPaise;
  }
}
