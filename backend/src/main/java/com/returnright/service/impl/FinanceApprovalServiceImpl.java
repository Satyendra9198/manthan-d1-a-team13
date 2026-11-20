package com.returnright.service.impl;

import com.returnright.service.FinanceApprovalService;
import org.springframework.stereotype.Service;

@Service
public class FinanceApprovalServiceImpl implements FinanceApprovalService {

  @Override
  public boolean requiresApproval(long refundPaise) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
