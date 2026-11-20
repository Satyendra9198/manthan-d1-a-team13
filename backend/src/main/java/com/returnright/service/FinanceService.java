package com.returnright.service;

import com.returnright.model.domain.Actor;
import com.returnright.model.dto.response.ReconciliationResponse;
import com.returnright.model.dto.response.RefundResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.enums.RefundStatus;
import java.util.List;
import java.util.UUID;

public interface FinanceService {

  /**
   * Approves a high-value refund and pays it out. Repeating the call (double click, retry) never
   * pays twice: the refund is keyed on the return and on {@code idempotencyKey}.
   */
  ReturnResponse approve(UUID returnId, String idempotencyKey, Actor actor);

  ReturnResponse reject(UUID returnId, String reason, Actor actor);

  /**
   * @param status optional filter, e.g. {@code UNKNOWN} to see refunds awaiting reconciliation
   */
  List<RefundResponse> listRefunds(RefundStatus status);

  ReconciliationResponse reconcile();
}
