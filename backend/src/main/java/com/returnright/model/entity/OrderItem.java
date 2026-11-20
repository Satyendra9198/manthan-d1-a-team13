package com.returnright.model.entity;

import com.returnright.model.enums.ProductCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of an order. Delivery is tracked per line because items can ship separately, and the
 * return window starts at delivery (assumption A-01).
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private CustomerOrder order;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "sku", nullable = false, updatable = false)
  private String sku;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(name = "variant")
  private String variant;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private ProductCategory category;

  @Column(name = "unit_price_paise", nullable = false, updatable = false)
  private long unitPricePaise;

  @Column(name = "quantity", nullable = false, updatable = false)
  private int quantity;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  protected OrderItem() {}

  public OrderItem(
      UUID id,
      int lineNumber,
      String sku,
      String productName,
      String variant,
      ProductCategory category,
      long unitPricePaise,
      int quantity,
      Instant deliveredAt) {
    this.id = id;
    this.lineNumber = lineNumber;
    this.sku = sku;
    this.productName = productName;
    this.variant = variant;
    this.category = category;
    this.unitPricePaise = unitPricePaise;
    this.quantity = quantity;
    this.deliveredAt = deliveredAt;
  }

  void attachTo(CustomerOrder order) {
    this.order = order;
  }

  public UUID getId() {
    return id;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public String getSku() {
    return sku;
  }

  public String getProductName() {
    return productName;
  }

  public String getVariant() {
    return variant;
  }

  public ProductCategory getCategory() {
    return category;
  }

  public long getUnitPricePaise() {
    return unitPricePaise;
  }

  public int getQuantity() {
    return quantity;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }
}
