package com.returnright.model.dto.response;

import java.util.UUID;

public record FlaggedCustomerResponse(
    UUID customerId, String fullName, String email, long flaggedReturns) {}
