package com.returnright.service;

import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.response.FinanceQueueItemResponse;
import com.returnright.model.dto.response.FlaggedReturnResponse;
import com.returnright.model.dto.response.ReviewDecisionResponse;
import java.util.List;

/**
 * Decisions held for a person: finance reviews refunds above ₹10,000 (A-08) and operations reviews
 * returns from flagged customers (A-11). Unknown RMAs throw {@link
 * com.returnright.exception.ReturnNotFoundException}; a return not waiting for that decision throws
 * {@link com.returnright.exception.IllegalTransitionException}.
 */
public interface ReviewService {

  List<FinanceQueueItemResponse> refundsAwaitingFinance();

  /** Approves the refund and pays it out at once. */
  ReviewDecisionResponse approveRefund(String rmaNumber, StaffActor reviewer);

  /** Declines the refund; the return is rejected with {@code REFUND_DECLINED}. */
  ReviewDecisionResponse declineRefund(String rmaNumber, String note, StaffActor reviewer);

  List<FlaggedReturnResponse> flaggedReturns();

  /** Approves a flagged return; the customer's label becomes available. */
  ReviewDecisionResponse approveReturn(String rmaNumber, StaffActor manager);

  /** Declines a flagged return; it is rejected with {@code REQUEST_DECLINED}. */
  ReviewDecisionResponse declineReturn(String rmaNumber, String note, StaffActor manager);
}
