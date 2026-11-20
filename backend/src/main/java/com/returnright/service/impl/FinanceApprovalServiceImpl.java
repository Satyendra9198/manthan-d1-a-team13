package com.returnright.service.impl;

import com.returnright.service.FinanceApprovalService;
import org.springframework.stereotype.Service;

/**
 * Refunds strictly above ₹10,000.00 per return, after the grade adjustment, need a finance
 * reviewer; exactly ₹10,000.00 does not (assumption A-08).
 */
@Service
public class FinanceApprovalServiceImpl implements FinanceApprovalService {

  static final long APPROVAL_THRESHOLD_PAISE = 1_000_000;

  @Override
  public boolean requiresApproval(long refundPaise) {
    return refundPaise > APPROVAL_THRESHOLD_PAISE;
  }
}
