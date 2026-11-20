package com.returnright.orders;

import com.returnright.core.enums.OrderStatus;
import com.returnright.core.enums.PaymentMethod;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderNumber;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    private String paymentReference;
    private Instant placedAt;
    private Instant deliveredAt;
    private long totalPaise;
    @OneToMany(mappedBy = "order")
    @OrderBy("lineNo")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(String orderNumber, Customer customer, OrderStatus status, PaymentMethod paymentMethod,
                 String paymentReference, Instant placedAt, Instant deliveredAt, long totalPaise) {
        this.orderNumber = orderNumber;
        this.customer = customer;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.paymentReference = paymentReference;
        this.placedAt = placedAt;
        this.deliveredAt = deliveredAt;
        this.totalPaise = totalPaise;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Customer getCustomer() { return customer; }
    public OrderStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getPaymentReference() { return paymentReference; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public long getTotalPaise() { return totalPaise; }
    public List<OrderItem> getItems() { return items; }
}
