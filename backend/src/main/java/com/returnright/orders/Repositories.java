package com.returnright.orders;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Spring Data repositories for the orders module. */
public final class Repositories {

    private Repositories() {}

    public interface CategoryRepository extends JpaRepository<Category, Short> {
        Optional<Category> findByCode(String code);
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {
        Optional<Product> findBySku(String sku);
    }

    public interface CustomerRepository extends JpaRepository<Customer, Long> {
        @Query("select c from Customer c where lower(c.email) = lower(?1)")
        Optional<Customer> findByEmailIgnoreCase(String email);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select c from Customer c where c.id = ?1")
        Optional<Customer> findForUpdate(long id);

        List<Customer> findByFlaggedTrueOrderByFlaggedAtDesc();
    }

    public interface OrderRepository extends JpaRepository<Order, Long> {
        Optional<Order> findByOrderNumber(String orderNumber);

        @Query("select o from Order o where o.customer.id = ?1 order by o.placedAt desc")
        List<Order> findByCustomer(long customerId);
    }

    public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
        @Query("select i from OrderItem i where i.order.id = ?1 order by i.lineNo")
        List<OrderItem> findByOrder(long orderId);
    }
}
