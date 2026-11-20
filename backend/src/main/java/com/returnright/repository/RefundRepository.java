package com.returnright.repository;

import com.returnright.model.entity.Refund;
import com.returnright.model.enums.RefundStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Refund persistence. Saving a second refund for the same return violates {@code
 * uq_refund_return_id} and raises {@link org.springframework.dao.DataIntegrityViolationException}.
 */
public interface RefundRepository extends JpaRepository<Refund, UUID> {

  Optional<Refund> findByIdempotencyKey(String idempotencyKey);

  Optional<Refund> findByReturnId(UUID returnId);

  List<Refund> findByStatusIn(Collection<RefundStatus> statuses);
}
