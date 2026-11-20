package com.returnright.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** One line per item in the return. */
public record InspectionRequest(@NotEmpty List<@Valid InspectionLineRequest> lines) {}
