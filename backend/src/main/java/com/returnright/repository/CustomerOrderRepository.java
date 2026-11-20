package com.returnright.repository;

import com.returnright.model.entity.CustomerOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read access to orders. Callers normalise the order number and email before calling. */
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

  /** Loads the order and its items in one query. */
  @EntityGraph(attributePaths = "items")
  Optional<CustomerOrder> findByOrderNumberAndCustomerEmail(
      String orderNumber, String customerEmail);

  /**
   * Takes a row lock on the order until the transaction ends, so two returns for the same order are
   * created one after the other and cannot both claim the same units.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from CustomerOrder o where o.id = :id")
  Optional<CustomerOrder> lockById(@Param("id") UUID id);
}
