package com.returnright.orders.repository;

import com.returnright.orders.entity.Customer;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("select c from Customer c where lower(c.email) = lower(?1)")
    Optional<Customer> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = ?1")
    Optional<Customer> findForUpdate(long id);

    List<Customer> findByFlaggedTrueOrderByFlaggedAtDesc();
}
