package com.returnright.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String addressLine1;
    private String city;
    private String state;
    private String pincode;
    @Column(name = "is_flagged")
    private boolean flagged;
    private Instant flaggedAt;
    private String flagReason;
    private Instant createdAt;

    protected Customer() {}

    public Customer(String email, String fullName, String phone, String addressLine1, String city, String state,
                    String pincode, Instant createdAt) {
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.createdAt = createdAt;
    }

    /** [A-13][A-14] Flag for finance review. Never blocks returns. */
    public void flag(Instant at, String reason) {
        this.flagged = true;
        this.flaggedAt = at;
        this.flagReason = reason;
    }

    /** [A-15] Manual clear by Ops Manager. */
    public void clearFlag() {
        this.flagged = false;
        this.flaggedAt = null;
        this.flagReason = null;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPincode() { return pincode; }
    public boolean isFlagged() { return flagged; }
    public Instant getFlaggedAt() { return flaggedAt; }
    public String getFlagReason() { return flagReason; }
    public Instant getCreatedAt() { return createdAt; }
}
