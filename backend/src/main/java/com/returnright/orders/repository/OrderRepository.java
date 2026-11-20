package com.returnright.orders.repository;

import com.returnright.orders.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("select o from Order o where o.customer.id = ?1 order by o.placedAt desc")
    List<Order> findByCustomer(long customerId);
}
