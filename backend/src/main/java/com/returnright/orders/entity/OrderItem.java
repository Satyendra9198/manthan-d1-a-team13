package com.returnright.orders.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    private Short lineNo;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;
    private Short quantity;
    private long unitPricePaise; // net paid per unit [A-11]

    protected OrderItem() {}

    public OrderItem(Order order, int lineNo, Product product, int quantity, long unitPricePaise) {
        this.order = order;
        this.lineNo = (short) lineNo;
        this.product = product;
        this.quantity = (short) quantity;
        this.unitPricePaise = unitPricePaise;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public int getLineNo() { return lineNo; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public long getUnitPricePaise() { return unitPricePaise; }
}
