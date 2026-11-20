package com.returnright.orders.repository;

import com.returnright.orders.entity.OrderItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("select i from OrderItem i where i.order.id = ?1 order by i.lineNo")
    List<OrderItem> findByOrder(long orderId);
}
