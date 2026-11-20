package com.returnright.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.enums.ProductCategory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs against real PostgreSQL so the Flyway migrations and entity mappings are checked too. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CustomerOrderRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private CustomerOrderRepository customerOrderRepository;

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void saveOrderWithTwoLines() {
    CustomerOrder order =
        new CustomerOrder(
            UUID.randomUUID(),
            "RR-10482",
            "aditi.sharma@example.com",
            "Aditi Sharma",
            Instant.parse("2026-11-10T04:30:00Z"));
    order.addItem(line(2, ProductCategory.INNERWEAR, "Cotton briefs (3-pack)"));
    order.addItem(line(1, ProductCategory.APPAREL, "Linen shirt"));
    customerOrderRepository.saveAndFlush(order);
    entityManager.clear();
  }

  @Test
  void matchingNumberAndEmailLoadsTheOrderWithItemsInLineOrder() {
    Optional<CustomerOrder> found =
        customerOrderRepository.findByOrderNumberAndCustomerEmail(
            "RR-10482", "aditi.sharma@example.com");

    assertThat(found).isPresent();
    assertThat(Hibernate.isInitialized(found.get().getItems())).isTrue();
    assertThat(found.get().getItems())
        .extracting(OrderItem::getProductName)
        .containsExactly("Linen shirt", "Cotton briefs (3-pack)");
  }

  @Test
  void differentEmailFindsNothing() {
    assertThat(
            customerOrderRepository.findByOrderNumberAndCustomerEmail(
                "RR-10482", "someone.else@example.com"))
        .isEmpty();
  }

  private static OrderItem line(int lineNumber, ProductCategory category, String productName) {
    return new OrderItem(
        UUID.randomUUID(),
        lineNumber,
        "SKU-" + lineNumber,
        productName,
        null,
        category,
        99_900,
        1,
        Instant.parse("2026-11-15T04:30:00Z"));
  }
}
