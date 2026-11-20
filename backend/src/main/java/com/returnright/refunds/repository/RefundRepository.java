package com.returnright.refunds.repository;

import com.returnright.refunds.entity.Refund;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByReturnId(UUID returnId);

    @Query("select r from Refund r where r.returnId in ?1")
    List<Refund> findByReturnIds(List<UUID> ids);
}
