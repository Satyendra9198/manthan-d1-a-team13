package com.returnright.support;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.returnright.model.entity.Refund;
import com.returnright.model.enums.RefundStatus;
import com.returnright.repository.RefundRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * In-memory {@link RefundRepository} for unit tests. It stores snapshots, so a change to a refund
 * is only visible after it is saved, and it enforces {@code uq_refund_return_id} like the database.
 *
 * <p>Only the methods the refund service uses are implemented; {@link #create()} fills in the rest
 * of {@code JpaRepository}.
 */
public abstract class InMemoryRefundRepository implements RefundRepository {

  private final Map<UUID, Refund> refundsById = new LinkedHashMap<>();
  private Refund concurrentWinner;

  public static InMemoryRefundRepository create() {
    return mock(
        InMemoryRefundRepository.class,
        withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
  }

  /** On the next insert for the same return, this refund is committed first, as if by a racer. */
  public void simulateConcurrentInsert(Refund winner) {
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
  public List<Refund> findByStatusIn(Collection<RefundStatus> statuses) {
    return refundsById.values().stream()
        .filter(refund -> statuses.contains(refund.getStatus()))
        .map(InMemoryRefundRepository::copy)
        .toList();
  }

  @Override
  public Optional<Refund> findById(UUID id) {
    return Optional.ofNullable(refundsById.get(id)).map(InMemoryRefundRepository::copy);
  }

  @Override
  public <S extends Refund> S save(S refund) {
    return store(refund);
  }

  @Override
  public <S extends Refund> S saveAndFlush(S refund) {
    return store(refund);
  }

  public Refund get(UUID refundId) {
    return copy(refundsById.get(refundId));
  }

  @Override
  public long count() {
    return refundsById.size();
  }

  private <S extends Refund> S store(S refund) {
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
      throw new DataIntegrityViolationException("uq_refund_return_id");
    }
    refundsById.put(refund.getId(), copy(refund));
    return refund;
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
