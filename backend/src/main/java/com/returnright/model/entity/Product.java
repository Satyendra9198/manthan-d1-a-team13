package com.returnright.model.entity;

import com.returnright.model.enums.ProductCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {

  @Id private UUID id;

  @Column(name = "sku", nullable = false)
  private String sku;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private ProductCategory category;

  @Column(name = "price_paise", nullable = false)
  private long pricePaise;

  protected Product() {}

  public Product(UUID id, String sku, String name, ProductCategory category, long pricePaise) {
    this.id = id;
    this.sku = sku;
    this.name = name;
    this.category = category;
    this.pricePaise = pricePaise;
  }

  public UUID getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public ProductCategory getCategory() {
    return category;
  }

  public long getPricePaise() {
    return pricePaise;
  }
}
