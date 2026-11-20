package com.returnright.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

  @Id private UUID id;

  @Column(name = "order_number", nullable = false)
  private String orderNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Column(name = "ordered_at", nullable = false)
  private Instant orderedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "payment_method", nullable = false)
  private String paymentMethod;

  @OneToMany(mappedBy = "order")
  private List<OrderItem> items = new ArrayList<>();

  protected CustomerOrder() {}

  public CustomerOrder(
      UUID id,
      String orderNumber,
      Customer customer,
      Instant orderedAt,
      Instant deliveredAt,
      String paymentMethod) {
    this.id = id;
    this.orderNumber = orderNumber;
    this.customer = customer;
    this.orderedAt = orderedAt;
    this.deliveredAt = deliveredAt;
    this.paymentMethod = paymentMethod;
  }

  public Optional<OrderItem> findItem(UUID orderItemId) {
    return items.stream().filter(item -> item.getId().equals(orderItemId)).findFirst();
  }

  public UUID getId() {
    return id;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public Customer getCustomer() {
    return customer;
  }

  public Instant getOrderedAt() {
    return orderedAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public List<OrderItem> getItems() {
    return items;
  }
}
