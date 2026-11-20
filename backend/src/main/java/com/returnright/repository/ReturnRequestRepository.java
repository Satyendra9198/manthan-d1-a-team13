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

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

  @EntityGraph(
      attributePaths = {"order", "customer", "items", "items.orderItem", "items.orderItem.product"})
  Optional<ReturnRequest> findDetailedById(UUID id);

  Optional<ReturnRequest> findByLabelCode(String labelCode);

  @EntityGraph(attributePaths = {"order", "customer"})
  List<ReturnRequest> findByStatusOrderByCreatedAtAsc(ReturnStatus status);

  @EntityGraph(attributePaths = {"order", "customer"})
  List<ReturnRequest> findByFlaggedTrueAndStatusOrderByCreatedAtAsc(ReturnStatus status);

  @EntityGraph(attributePaths = {"order", "customer"})
  List<ReturnRequest> findTop200ByOrderByCreatedAtDesc();

  /** Units of an order line already on non-cancelled returns. */
  @Query(
      """
      select coalesce(sum(item.quantity), 0) from ReturnItem item
      where item.orderItem.id = :orderItemId and item.returnRequest.status <> :excluded
      """)
  long sumQuantityOnReturns(
      @Param("orderItemId") UUID orderItemId, @Param("excluded") ReturnStatus excluded);

  /** Creation times of a customer's non-cancelled requests since {@code since}. */
  @Query(
      """
      select r.createdAt from ReturnRequest r
      where r.customer.id = :customerId and r.status <> :excluded and r.createdAt >= :since
      """)
  List<Instant> findRequestTimes(
      @Param("customerId") UUID customerId,
      @Param("excluded") ReturnStatus excluded,
      @Param("since") Instant since);
}
