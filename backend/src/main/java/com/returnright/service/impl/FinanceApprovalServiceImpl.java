package com.returnright.service.impl;

import com.returnright.service.FinanceApprovalService;
import org.springframework.stereotype.Service;

/** Refunds strictly above 10,000.00 INR, after the grade adjustment, need a finance reviewer. */
@Service
public class FinanceApprovalServiceImpl implements FinanceApprovalService {

  static final long APPROVAL_THRESHOLD_PAISE = 1_000_000;

  @Override
  public boolean requiresApproval(long refundPaise) {
    return refundPaise > APPROVAL_THRESHOLD_PAISE;
  }
}
