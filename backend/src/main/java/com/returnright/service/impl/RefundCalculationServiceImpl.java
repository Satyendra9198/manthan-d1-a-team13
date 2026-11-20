package com.returnright.service.impl;

import com.returnright.model.domain.InspectedLine;
import com.returnright.model.enums.InspectionGrade;
import com.returnright.service.RefundCalculationService;
import java.util.List;
import org.springframework.stereotype.Service;

/** Line refund = unit price x accepted quantity x grade percentage, rounded down to the paisa. */
@Service
public class RefundCalculationServiceImpl implements RefundCalculationService {

  private static final int FULL_PERCENT = 100;
  private static final int GRADE_C_PERCENT = 50;

  @Override
  public long lineRefundPaise(InspectedLine line) {
    if (line.unitPricePaise() < 0) {
      throw new IllegalArgumentException("Unit price cannot be negative");
    }
    if (line.acceptedQuantity() < 0) {
      throw new IllegalArgumentException("Accepted quantity cannot be negative");
    }
    if (line.acceptedQuantity() > line.returnedQuantity()) {
      throw new IllegalArgumentException("Accepted quantity cannot exceed returned quantity");
    }
    if (line.acceptedQuantity() == 0) {
      return 0;
    }
    if (line.grade() == null) {
      throw new IllegalArgumentException("A grade is required for accepted items");
    }
    int percent = line.grade() == InspectionGrade.C ? GRADE_C_PERCENT : FULL_PERCENT;
    return line.unitPricePaise() * line.acceptedQuantity() * percent / FULL_PERCENT;
  }

  @Override
  public long totalRefundPaise(List<InspectedLine> lines) {
    return lines.stream().mapToLong(this::lineRefundPaise).sum();
  }
}
