package com.returnright.core.enums;

public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    IN_TRANSIT,
    RECEIVED,
    INSPECTED,
    PENDING_FINANCE,
    REFUND_PENDING,
    REFUND_FAILED,
    REFUNDED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == REFUNDED || this == REJECTED || this == CANCELLED;
    }
}
