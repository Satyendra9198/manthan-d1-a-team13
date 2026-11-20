package com.returnright.refunds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory repository that stores snapshots, so a refund change is only visible after {@link
 * #save}. Enforces the unique refund-per-return constraint like the database does.
 */
class InMemoryRefundRepository implements RefundRepository {

  private final Map<UUID, Refund> refundsById = new LinkedHashMap<>();
  private Refund concurrentWinner;

  /** On the next insert for the same return, this refund is committed first, as if by a racer. */
  void simulateConcurrentInsert(Refund winner) {
    this.concurrentWinner = winner;
  }

  @Override
  public Optional<Refund> findByIdempotencyKey(String idempotencyKey) {
    return refundsById.values().stream()
        .filter(refund -> refund.getIdempotencyKey().equals(idempotencyKey))
        .findFirst()
        .map(InMemoryRefundRepository::copy);
  }

  @Override
  public Optional<Refund> findByReturnId(UUID returnId) {
    return refundsById.values().stream()
        .filter(refund -> refund.getReturnId().equals(returnId))
        .findFirst()
        .map(InMemoryRefundRepository::copy);
  }

  @Override
  public List<Refund> findByStatusIn(Set<RefundStatus> statuses) {
    return refundsById.values().stream()
        .filter(refund -> statuses.contains(refund.getStatus()))
        .map(InMemoryRefundRepository::copy)
        .toList();
  }

  @Override
  public Refund save(Refund refund) {
    if (concurrentWinner != null && concurrentWinner.getReturnId().equals(refund.getReturnId())) {
      refundsById.put(concurrentWinner.getId(), copy(concurrentWinner));
      concurrentWinner = null;
    }
    boolean otherRefundForSameReturn =
        refundsById.values().stream()
            .anyMatch(
                existing ->
                    existing.getReturnId().equals(refund.getReturnId())
                        && !existing.getId().equals(refund.getId()));
    if (otherRefundForSameReturn) {
      throw new DuplicateRefundException(refund.getReturnId());
    }
    refundsById.put(refund.getId(), copy(refund));
    return refund;
  }

  Refund get(UUID refundId) {
    return copy(refundsById.get(refundId));
  }

  int count() {
    return refundsById.size();
  }

  private static Refund copy(Refund refund) {
    return new Refund(
        refund.getId(),
        refund.getReturnId(),
        refund.getAmountPaise(),
        refund.getMethod(),
        refund.getIdempotencyKey(),
        refund.getStatus(),
        refund.getGatewayTransactionId(),
        refund.getFailureReason());
  }
}
