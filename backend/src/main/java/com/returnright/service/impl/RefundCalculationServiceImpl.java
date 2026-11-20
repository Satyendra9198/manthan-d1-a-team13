package com.returnright.service.impl;

import com.returnright.model.domain.InspectedLine;
import com.returnright.service.RefundCalculationService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Refund per line = unit price × accepted quantity × grade % (A-06), in integer paise, rounded down
 * (A-07). A rejected line has accepted quantity 0 and refunds nothing.
 */
@Service
public class RefundCalculationServiceImpl implements RefundCalculationService {

  @Override
  public long lineRefundPaise(InspectedLine line) {
    if (line.acceptedQuantity() < 0 || line.acceptedQuantity() > line.returnedQuantity()) {
      throw new IllegalArgumentException(
          "Accepted quantity must be between 0 and the returned quantity "
              + line.returnedQuantity()
              + ", was "
              + line.acceptedQuantity());
    }
    long acceptedValuePaise = Math.multiplyExact(line.unitPricePaise(), line.acceptedQuantity());
    return Math.multiplyExact(acceptedValuePaise, line.grade().getRefundPercent()) / 100;
  }

  @Override
  public long totalRefundPaise(List<InspectedLine> lines) {
    return lines.stream().mapToLong(this::lineRefundPaise).sum();
  }
}
