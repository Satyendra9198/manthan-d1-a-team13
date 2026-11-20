package com.returnright.model.dto.response;

import java.util.List;

/**
 * Operations manager dashboard.
 *
 * @param averageCycleTimeHours request-to-refund average, {@code null} when nothing is refunded yet
 */
public record DashboardResponse(
    List<CategoryReturnRateResponse> returnRateByCategory,
    List<ReasonCountResponse> topReasons,
    Double averageCycleTimeHours,
    List<FlaggedCustomerResponse> flaggedCustomers) {}
