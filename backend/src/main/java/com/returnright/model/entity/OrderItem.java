package com.returnright.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private CustomerOrder order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "unit_price_paise", nullable = false)
  private long unitPricePaise;

  protected OrderItem() {}

  public OrderItem(
      UUID id, CustomerOrder order, Product product, int quantity, long unitPricePaise) {
    this.id = id;
    this.order = order;
    this.product = product;
    this.quantity = quantity;
    this.unitPricePaise = unitPricePaise;
  }

  public UUID getId() {
    return id;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public Product getProduct() {
    return product;
  }

  public int getQuantity() {
    return quantity;
  }

  public long getUnitPricePaise() {
    return unitPricePaise;
  }
}
