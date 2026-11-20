package com.returnright.model.entity;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnReason;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** One order line on a return, with the customer's reason and photos. */
@Entity
@Table(name = "return_item")
public class ReturnItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "return_id", nullable = false, updatable = false)
  private ReturnRequest returnRequest;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
  private OrderItem orderItem;

  @Column(name = "quantity", nullable = false, updatable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, updatable = false)
  private ReturnReason reason;

  @Column(name = "customer_comment", updatable = false)
  private String customerComment;

  @Enumerated(EnumType.STRING)
  @Column(name = "grade")
  private InspectionGrade grade;

  /** Units the warehouse accepted (A-05); {@code null} before inspection. */
  @Column(name = "accepted_quantity")
  private Integer acceptedQuantity;

  /** Why some or all units were not accepted; {@code null} when all were. */
  @Enumerated(EnumType.STRING)
  @Column(name = "rejection_reason")
  private RejectionReason rejectionReason;

  @Column(name = "inspection_note")
  private String inspectionNote;

  @OneToMany(mappedBy = "returnItem", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ReturnPhoto> photos = new ArrayList<>();

  protected ReturnItem() {}

  public ReturnItem(
      UUID id, OrderItem orderItem, int quantity, ReturnReason reason, String customerComment) {
    this.id = id;
    this.orderItem = orderItem;
    this.quantity = quantity;
    this.reason = reason;
    this.customerComment = customerComment;
  }

  void attachTo(ReturnRequest returnRequest) {
    this.returnRequest = returnRequest;
  }

  /** Records the warehouse result for this line. */
  public void recordInspection(
      InspectionGrade grade,
      int acceptedQuantity,
      RejectionReason rejectionReason,
      String inspectionNote) {
    this.grade = grade;
    this.acceptedQuantity = acceptedQuantity;
    this.rejectionReason = rejectionReason;
    this.inspectionNote = inspectionNote;
  }

  public void addPhoto(ReturnPhoto photo) {
    photo.attachTo(this);
    photos.add(photo);
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

  public String getCustomerComment() {
    return customerComment;
  }

  public InspectionGrade getGrade() {
    return grade;
  }

  public Integer getAcceptedQuantity() {
    return acceptedQuantity;
  }

  public RejectionReason getRejectionReason() {
    return rejectionReason;
  }

  public String getInspectionNote() {
    return inspectionNote;
  }

  public List<ReturnPhoto> getPhotos() {
    return Collections.unmodifiableList(photos);
  }
}
