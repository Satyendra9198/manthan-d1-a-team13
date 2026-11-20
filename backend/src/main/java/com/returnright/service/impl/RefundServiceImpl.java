package com.returnright.service.impl;

import com.returnright.client.PaymentGatewayClient;
import com.returnright.model.domain.IssueRefundCommand;
import com.returnright.model.entity.Refund;
import com.returnright.repository.RefundRepository;
import com.returnright.service.RefundService;
import org.springframework.stereotype.Service;

@Service
public class RefundServiceImpl implements RefundService {

  private final RefundRepository refundRepository;
  private final PaymentGatewayClient paymentGatewayClient;

  public RefundServiceImpl(
      RefundRepository refundRepository, PaymentGatewayClient paymentGatewayClient) {
    this.refundRepository = refundRepository;
    this.paymentGatewayClient = paymentGatewayClient;
  }

  @Override
  public Refund issueRefund(IssueRefundCommand command) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public int reconcileUnresolvedRefunds() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
