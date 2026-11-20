package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.returnright.exception.OrderNotFoundException;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.OrderItemResponse;
import com.returnright.model.dto.response.OrderLookupResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.enums.EligibilityReason;
import com.returnright.model.enums.ProductCategory;
import com.returnright.repository.CustomerOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderLookupServiceImplTest {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  /** 20 Nov 2026, 12:00 IST. */
  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");

  private static final String ORDER_NUMBER = "RR-10482";
  private static final String EMAIL = "aditi.sharma@example.com";

  private final CustomerOrderRepository customerOrderRepository =
      mock(CustomerOrderRepository.class);

  private final OrderLookupServiceImpl service =
      new OrderLookupServiceImpl(
          customerOrderRepository, new EligibilityServiceImpl(Clock.fixed(NOW, ZoneOffset.UTC)));

  @Test
  void matchingOrderNumberAndEmailReturnsTheOrderWithEveryLine() {
    CustomerOrder order = order(apparel(deliveredDaysAgo(5)), innerwear(deliveredDaysAgo(5)));
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(ORDER_NUMBER, EMAIL))
        .thenReturn(Optional.of(order));

    OrderLookupResponse response = service.findOrder(new OrderLookupRequest(ORDER_NUMBER, EMAIL));

    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
    assertThat(response.items()).hasSize(2);
  }

  @Test
  void lookupIgnoresCaseAndSurroundingSpaces() {
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(ORDER_NUMBER, EMAIL))
        .thenReturn(Optional.of(order(apparel(deliveredDaysAgo(5)))));

    OrderLookupResponse response =
        service.findOrder(new OrderLookupRequest("  rr-10482 ", " Aditi.Sharma@Example.COM "));

    assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
  }

  @Test
  void wrongEmailOrUnknownOrderIsRefusedAsNotFound() {
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(anyString(), anyString()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.findOrder(new OrderLookupRequest(ORDER_NUMBER, "someone@example.com")))
        .isInstanceOf(OrderNotFoundException.class)
        .hasMessageContaining("couldn't find that order");
  }

  @Test
  void returnableLineOffersItsFullQuantityAndLastReturnDate() {
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(ORDER_NUMBER, EMAIL))
        .thenReturn(Optional.of(order(apparel(deliveredDaysAgo(5)))));

    OrderItemResponse line =
        service.findOrder(new OrderLookupRequest(ORDER_NUMBER, EMAIL)).items().get(0);

    assertThat(line.returnable()).isTrue();
    assertThat(line.eligibilityReason()).isEqualTo(EligibilityReason.ELIGIBLE);
    assertThat(line.returnableQuantity()).isEqualTo(2);
    assertThat(line.lastReturnDate()).isEqualTo(LocalDate.of(2026, 12, 15));
  }

  @Test
  void nonReturnableLineIsListedWithItsReasonAndZeroReturnableQuantity() {
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(ORDER_NUMBER, EMAIL))
        .thenReturn(Optional.of(order(innerwear(deliveredDaysAgo(5)))));

    OrderItemResponse line =
        service.findOrder(new OrderLookupRequest(ORDER_NUMBER, EMAIL)).items().get(0);

    assertThat(line.returnable()).isFalse();
    assertThat(line.eligibilityReason()).isEqualTo(EligibilityReason.NON_RETURNABLE_CATEGORY);
    assertThat(line.eligibilityMessage()).isNotBlank();
    assertThat(line.returnableQuantity()).isZero();
  }

  @Test
  void returnableItemCountExcludesExpiredAndNonReturnableLines() {
    OrderItem expiredElectronics =
        item(ProductCategory.ELECTRONICS, "Wireless earbuds Pro", 1, deliveredDaysAgo(11));
    CustomerOrder order =
        order(apparel(deliveredDaysAgo(5)), innerwear(deliveredDaysAgo(5)), expiredElectronics);
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(ORDER_NUMBER, EMAIL))
        .thenReturn(Optional.of(order));

    OrderLookupResponse response = service.findOrder(new OrderLookupRequest(ORDER_NUMBER, EMAIL));

    assertThat(response.returnableItemCount()).isEqualTo(1);
    assertThat(response.items().get(2).eligibilityReason())
        .isEqualTo(EligibilityReason.RETURN_WINDOW_EXPIRED);
  }

  private static CustomerOrder order(OrderItem... items) {
    CustomerOrder order =
        new CustomerOrder(
            UUID.randomUUID(), ORDER_NUMBER, EMAIL, "Aditi Sharma", NOW.minusSeconds(864_000));
    for (OrderItem item : items) {
      order.addItem(item);
    }
    return order;
  }

  private static OrderItem apparel(Instant deliveredAt) {
    return item(ProductCategory.APPAREL, "Linen shirt", 2, deliveredAt);
  }

  private static OrderItem innerwear(Instant deliveredAt) {
    return item(ProductCategory.INNERWEAR, "Cotton briefs (3-pack)", 1, deliveredAt);
  }

  private static OrderItem item(
      ProductCategory category, String productName, int quantity, Instant deliveredAt) {
    return new OrderItem(
        UUID.randomUUID(),
        1,
        "SKU-" + category,
        productName,
        null,
        category,
        189_900,
        quantity,
        deliveredAt);
  }

  /** Delivered at 10:00 IST, {@code days} calendar days before 20 Nov 2026. */
  private static Instant deliveredDaysAgo(int days) {
    return LocalDate.of(2026, 11, 20)
        .minusDays(days)
        .atTime(LocalTime.of(10, 0))
        .atZone(IST)
        .toInstant();
  }
}
