package com.returnright.core.enums;

public enum Role {
    CUSTOMER,
    WAREHOUSE_AGENT,
    FINANCE_REVIEWER,
    OPS_MANAGER,
    ADMIN,
    SYSTEM;

    public boolean isStaff() {
        return this == WAREHOUSE_AGENT || this == FINANCE_REVIEWER || this == OPS_MANAGER || this == ADMIN;
    }
}
