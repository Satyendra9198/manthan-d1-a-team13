package com.returnright.returns;

import com.returnright.core.enums.ReturnStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReturnRepository extends JpaRepository<ReturnRequest, UUID> {

    /** SELECT ... FOR UPDATE: serialises every transition on one return (Part 1 §4.5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.id = ?1")
    Optional<ReturnRequest> findForUpdate(UUID id);

    Optional<ReturnRequest> findByRmaNumber(String rmaNumber);

    @Query("select r from ReturnRequest r where r.customer.id = ?1 order by r.createdAt desc")
    List<ReturnRequest> findByCustomer(long customerId);

    @Query("select r from ReturnRequest r where r.status in ?1 order by r.updatedAt asc")
    List<ReturnRequest> findByStatusIn(Collection<ReturnStatus> statuses, Pageable page);

    @Query("select count(r) from ReturnRequest r where r.status in ?1")
    long countByStatusIn(Collection<ReturnStatus> statuses);

    @Query("select count(r) from ReturnRequest r where r.customer.id = ?1 and r.createdAt > ?2 and r.createdAt <= ?3")
    long countCreatedBetween(long customerId, Instant fromExclusive, Instant toInclusive);

    @Query(value = "SELECT nextval('rma_seq')", nativeQuery = true)
    long nextRmaSeq();
}
