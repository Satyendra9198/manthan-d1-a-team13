package com.returnright.service;

/** Decides whether a refund needs a finance reviewer (assumption A-08). */
public interface FinanceApprovalService {

  boolean requiresApproval(long refundPaise);
}
