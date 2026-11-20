package com.returnright.repository;

import com.returnright.model.entity.CustomerOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

  @EntityGraph(attributePaths = {"customer", "items", "items.product"})
  Optional<CustomerOrder> findByOrderNumber(String orderNumber);
}
