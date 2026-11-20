package com.returnright.model.dto.request;

import com.returnright.model.enums.InspectionGrade;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inspection result for one return item. {@code acceptedQuantity} 0 rejects the line; a grade is
 * required when any unit is accepted.
 */
public record InspectionLineRequest(
    @NotNull UUID returnItemId,
    @Min(0) int acceptedQuantity,
    InspectionGrade grade,
    @Size(max = 500) String notes) {}
