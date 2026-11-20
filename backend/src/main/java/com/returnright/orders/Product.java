package com.returnright.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sku;
    private String name;
    private String brand;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;
    private long pricePaise;

    protected Product() {}

    public Product(String sku, String name, String brand, Category category, long pricePaise) {
        this.sku = sku;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.pricePaise = pricePaise;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public Category getCategory() { return category; }
    public long getPricePaise() { return pricePaise; }
}
