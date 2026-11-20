package com.returnright.refunds;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence port for refunds. The database enforces one refund per return. */
public interface RefundRepository {

  Optional<Refund> findByIdempotencyKey(String idempotencyKey);

  Optional<Refund> findByReturnId(UUID returnId);

  List<Refund> findByStatusIn(Set<RefundStatus> statuses);

  /**
   * Inserts or updates a refund.
   *
   * @throws DuplicateRefundException if a different refund already exists for the same return
   */
  Refund save(Refund refund);
}
