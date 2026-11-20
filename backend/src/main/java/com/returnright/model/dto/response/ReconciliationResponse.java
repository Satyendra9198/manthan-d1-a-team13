package com.returnright.model.dto.response;

/**
 * Result of re-driving unresolved refunds.
 *
 * @param refundsSettled refunds that reached SUCCEEDED or FAILED
 * @param returnsCompleted returns moved to REFUNDED as a result
 */
public record ReconciliationResponse(int refundsSettled, int returnsCompleted) {}
