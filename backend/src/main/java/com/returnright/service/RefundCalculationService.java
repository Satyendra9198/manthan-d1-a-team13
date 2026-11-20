package com.returnright.service;

import com.returnright.model.domain.InspectedLine;
import java.util.List;

/** Refund amounts from inspection results (assumptions A-05, A-06, A-07). */
public interface RefundCalculationService {

  long lineRefundPaise(InspectedLine line);

  long totalRefundPaise(List<InspectedLine> lines);
}
