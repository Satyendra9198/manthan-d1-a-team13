package com.returnright.service.impl;

import com.returnright.model.domain.InspectedLine;
import com.returnright.service.RefundCalculationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RefundCalculationServiceImpl implements RefundCalculationService {

  @Override
  public long lineRefundPaise(InspectedLine line) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public long totalRefundPaise(List<InspectedLine> lines) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
