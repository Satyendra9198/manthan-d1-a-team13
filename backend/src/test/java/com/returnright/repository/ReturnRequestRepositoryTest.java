package com.returnright.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.returnright.model.domain.ReturnedQuantity;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnPhoto;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.ReturnReason;
import com.returnright.model.enums.ReturnStatus;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs against real PostgreSQL so the V3 migration, sequence and JPQL queries are checked. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ReturnRequestRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");
  private static final String EMAIL = "aditi.sharma@example.com";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private CustomerOrderRepository customerOrderRepository;
  @Autowired private ReturnRequestRepository returnRequestRepository;
  @Autowired private ReturnItemRepository returnItemRepository;
  @Autowired private EntityManager entityManager;

  private CustomerOrder order;
  private OrderItem shirts;
  private OrderItem mugs;

  @BeforeEach
  void saveOrder() {
    order = new CustomerOrder(UUID.randomUUID(), "RR-10482", EMAIL, "Aditi Sharma", NOW);
    shirts = orderItem(1, "Linen shirt", 3);
    mugs = orderItem(2, "Ceramic mug set", 1);
    order.addItem(shirts);
    order.addItem(mugs);
    customerOrderRepository.saveAndFlush(order);
  }

  @Test
  void returnedQuantitiesAreSummedPerItemAndSkipCancelledReturns() {
    saveReturn(ReturnStatus.APPROVED, NOW, shirts, 1);
    saveReturn(ReturnStatus.REQUESTED, NOW, shirts, 1);
    saveReturn(ReturnStatus.CANCELLED, NOW, shirts, 1);
    saveReturn(ReturnStatus.CANCELLED, NOW, mugs, 1);

    assertThat(returnItemRepository.findReturnedQuantities(order.getId()))
        .extracting(ReturnedQuantity::orderItemId, ReturnedQuantity::quantity)
        .containsExactly(tuple(shirts.getId(), 2L));
  }

  @Test
  void requestTimesSkipTheExcludedStatusAndAnythingBeforeTheCutoff() {
    saveReturn(ReturnStatus.APPROVED, NOW.minus(Duration.ofDays(5)), shirts, 1);
    saveReturn(ReturnStatus.CANCELLED, NOW.minus(Duration.ofDays(4)), shirts, 1);
    saveReturn(ReturnStatus.REQUESTED, NOW.minus(Duration.ofDays(31)), mugs, 1);

    assertThat(
            returnRequestRepository.findRequestTimes(
                EMAIL, ReturnStatus.CANCELLED, NOW.minus(Duration.ofDays(30))))
        .containsExactly(NOW.minus(Duration.ofDays(5)));
  }

  @Test
  void rmaSequenceHandsOutIncreasingNumbers() {
    long first = returnRequestRepository.nextRmaSequence();

    assertThat(returnRequestRepository.nextRmaSequence()).isEqualTo(first + 1);
  }

  @Test
  void returnIsSavedWithItsItemsAndPhotos() {
    ReturnRequest saved = saveReturn(ReturnStatus.APPROVED, NOW, shirts, 2);
    entityManager.clear();

    ReturnRequest loaded = returnRequestRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getRmaNumber()).isEqualTo(saved.getRmaNumber());
    assertThat(loaded.getItems())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getQuantity()).isEqualTo(2);
              assertThat(item.getReason()).isEqualTo(ReturnReason.DAMAGED_ON_ARRIVAL);
              assertThat(item.getPhotos())
                  .extracting(ReturnPhoto::getContentType)
                  .containsExactly("image/jpeg");
            });
  }

  @Test
  void lockingAnOrderReturnsIt() {
    assertThat(customerOrderRepository.lockById(order.getId()))
        .map(CustomerOrder::getId)
        .contains(order.getId());
  }

  private ReturnRequest saveReturn(
      ReturnStatus status, Instant createdAt, OrderItem orderItem, int quantity) {
    ReturnRequest returnRequest =
        new ReturnRequest(
            UUID.randomUUID(),
            "RMA-2026-" + UUID.randomUUID().toString().substring(0, 8),
            order,
            RefundMethod.STORE_CREDIT,
            false,
            createdAt);
    returnRequest.moveTo(status, createdAt);
    ReturnItem returnItem =
        new ReturnItem(
            UUID.randomUUID(), orderItem, quantity, ReturnReason.DAMAGED_ON_ARRIVAL, null);
    UUID photoId = UUID.randomUUID();
    returnItem.addPhoto(
        new ReturnPhoto(
            photoId,
            "returns/" + returnRequest.getId() + "/" + photoId + ".jpg",
            "image/jpeg",
            1024,
            createdAt));
    returnRequest.addItem(returnItem);
    return returnRequestRepository.saveAndFlush(returnRequest);
  }

  private static OrderItem orderItem(int lineNumber, String productName, int quantity) {
    return new OrderItem(
        UUID.randomUUID(),
        lineNumber,
        "SKU-" + lineNumber,
        productName,
        null,
        ProductCategory.APPAREL,
        99_900,
        quantity,
        NOW);
  }
}
