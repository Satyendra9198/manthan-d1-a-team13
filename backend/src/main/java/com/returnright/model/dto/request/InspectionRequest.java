package com.returnright.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** "Complete inspection": one entry for every line on the return. */
public record InspectionRequest(
    @NotEmpty @Size(max = 50) List<@Valid @NotNull InspectedItemRequest> items) {}
