package com.returnright.repository;

import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Return persistence. Lookups by RMA load the order and the items with their order lines in one
 * query; photos and status history are batch-loaded on first access.
 */
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

  /** Creation times of the customer's returns since {@code since}, ignoring one status. */
  @Query(
      """
      select r.createdAt from ReturnRequest r
      where r.customerEmail = :email and r.status <> :excludedStatus and r.createdAt >= :since
      """)
  List<Instant> findRequestTimes(
      @Param("email") String email,
      @Param("excludedStatus") ReturnStatus excludedStatus,
      @Param("since") Instant since);

  /** Next value for the running part of the RMA number. Never reused, even on rollback. */
  @Query(value = "select nextval('rma_number_seq')", nativeQuery = true)
  long nextRmaSequence();

  @EntityGraph(attributePaths = {"order", "items", "items.orderItem"})
  Optional<ReturnRequest> findByRmaNumber(String rmaNumber);

  @EntityGraph(attributePaths = {"order", "items", "items.orderItem"})
  Optional<ReturnRequest> findByRmaNumberAndCustomerEmail(String rmaNumber, String customerEmail);

  @EntityGraph(attributePaths = {"order", "items", "items.orderItem"})
  List<ReturnRequest> findByStatusOrderByCreatedAtAsc(ReturnStatus status);

  @Query("select r.id from ReturnRequest r where r.status = :status")
  List<UUID> findIdsByStatus(@Param("status") ReturnStatus status);

  long countByInspectedAtGreaterThanEqualAndInspectedAtLessThan(Instant from, Instant to);

  long countByInspectedByAndInspectedAtGreaterThanEqualAndInspectedAtLessThan(
      UUID inspectedBy, Instant from, Instant to);
}
