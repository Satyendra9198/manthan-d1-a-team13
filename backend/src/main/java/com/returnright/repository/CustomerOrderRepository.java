package com.returnright.repository;

import com.returnright.model.entity.CustomerOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read access to orders. Callers normalise the order number and email before calling. */
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

  /** Loads the order and its items in one query. */
  @EntityGraph(attributePaths = "items")
  Optional<CustomerOrder> findByOrderNumberAndCustomerEmail(
      String orderNumber, String customerEmail);
}
