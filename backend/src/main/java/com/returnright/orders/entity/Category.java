package com.returnright.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    private Short id;
    private String code;
    private String name;
    @Column(name = "is_returnable")
    private boolean returnable;
    private Short returnWindowDays;

    protected Category() {}

    public Category(short id, String code, String name, boolean returnable, Integer windowDays) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.returnable = returnable;
        this.returnWindowDays = windowDays == null ? null : windowDays.shortValue();
    }

    public Short getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isReturnable() { return returnable; }
    public Integer getReturnWindowDays() { return returnWindowDays == null ? null : returnWindowDays.intValue(); }
}
