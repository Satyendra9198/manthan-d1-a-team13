package com.returnright.model.dto.response;

import com.returnright.model.enums.ProductCategory;

public record CategoryReturnRateResponse(
    ProductCategory category, long unitsSold, long unitsReturned, double returnRatePercent) {}
