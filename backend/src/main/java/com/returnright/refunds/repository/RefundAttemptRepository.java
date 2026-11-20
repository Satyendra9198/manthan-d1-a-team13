package com.returnright.refunds.repository;

import com.returnright.refunds.entity.RefundAttempt;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefundAttemptRepository extends JpaRepository<RefundAttempt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RefundAttempt a where a.id = ?1")
    Optional<RefundAttempt> findForUpdate(UUID id);

    @Query("select a from RefundAttempt a where a.refundId = ?1 order by a.attemptNo")
    List<RefundAttempt> findByRefund(UUID refundId);

    @Query("select a from RefundAttempt a where a.refundId in ?1 order by a.attemptNo")
    List<RefundAttempt> findByRefunds(List<UUID> refundIds);

    /** Reconciler candidates: UNKNOWN, or PENDING older than the stale cutoff (crash before/after call, F-07/F-08). */
    @Query("""
            select a.id from RefundAttempt a where a.method = com.returnright.core.enums.RefundMethod.ORIGINAL_PAYMENT
            and (a.status = com.returnright.core.enums.AttemptStatus.UNKNOWN
                 or (a.status = com.returnright.core.enums.AttemptStatus.PENDING and a.createdAt < ?1))
            order by a.createdAt""")
    List<UUID> findReconcileCandidates(Instant stalePendingBefore);
}
