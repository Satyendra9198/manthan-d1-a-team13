package com.returnright.repository;

import com.returnright.model.domain.ReturnedQuantity;
import com.returnright.model.entity.ReturnItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Return line persistence. */
public interface ReturnItemRepository extends JpaRepository<ReturnItem, UUID> {

  /** Units per order item of this order already on returns that were not cancelled. */
  @Query(
      """
      select new com.returnright.model.domain.ReturnedQuantity(ri.orderItem.id, sum(ri.quantity))
      from ReturnItem ri
      where ri.orderItem.order.id = :orderId
        and ri.returnRequest.status <> com.returnright.model.enums.ReturnStatus.CANCELLED
      group by ri.orderItem.id
      """)
  List<ReturnedQuantity> findReturnedQuantities(@Param("orderId") UUID orderId);
}
