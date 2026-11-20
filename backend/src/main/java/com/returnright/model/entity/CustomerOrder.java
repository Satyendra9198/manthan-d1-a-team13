package com.returnright.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An order placed in the store. Orders are created by the order system; ReturnRight only reads
 * them. Order numbers are stored upper-case and emails lower-case so lookups can match exactly.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "order_number", nullable = false, unique = true, updatable = false)
  private String orderNumber;

  @Column(name = "customer_email", nullable = false)
  private String customerEmail;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "placed_at", nullable = false, updatable = false)
  private Instant placedAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineNumber ASC")
  private List<OrderItem> items = new ArrayList<>();

  protected CustomerOrder() {}

  public CustomerOrder(
      UUID id, String orderNumber, String customerEmail, String customerName, Instant placedAt) {
    this.id = id;
    this.orderNumber = orderNumber;
    this.customerEmail = customerEmail;
    this.customerName = customerName;
    this.placedAt = placedAt;
  }

  public void addItem(OrderItem item) {
    item.attachTo(this);
    items.add(item);
  }

  public UUID getId() {
    return id;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public String getCustomerName() {
    return customerName;
  }

  public Instant getPlacedAt() {
    return placedAt;
  }

  public List<OrderItem> getItems() {
    return Collections.unmodifiableList(items);
  }
}
