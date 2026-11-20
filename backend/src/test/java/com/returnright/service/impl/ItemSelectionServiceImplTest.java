package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.returnright.exception.ItemSelectionRejectedException;
import com.returnright.exception.OrderNotFoundException;
import com.returnright.model.dto.request.ItemSelectionRequest;
import com.returnright.model.dto.request.SelectedItemRequest;
import com.returnright.model.dto.response.ItemSelectionResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.enums.ProductCategory;
import com.returnright.repository.CustomerOrderRepository;
import com.returnright.repository.ReturnItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemSelectionServiceImplTest {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  /** 20 Nov 2026, 12:00 IST. */
  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");

  private static final String ORDER_NUMBER = "RR-10482";
  private static final String EMAIL = "aditi.sharma@example.com";

  private final CustomerOrderRepository customerOrderRepository =
      mock(CustomerOrderRepository.class);

  private final ReturnItemRepository returnItemRepository = mock(ReturnItemRepository.class);

  private final ItemSelectionServiceImpl service =
      new ItemSelectionServiceImpl(
          new CustomerOrderReader(customerOrderRepository, returnItemRepository),
          new EligibilityServiceImpl(Clock.fixed(NOW, ZoneOffset.UTC)));

  /** Two linen shirts at ₹1,899, delivered 5 days ago. */
  private OrderItem linenShirts;

  /** One mug set at ₹1,249, delivered 5 days ago. */
  private OrderItem mugSet;

  /** Innerwear, never returnable. */
  private OrderItem briefs;

  /** Electronics delivered 11 days ago: the 10-day window has closed. */
  private OrderItem earbuds;

  @BeforeEach
  void stubOrder() {
    linenShirts = item(1, ProductCategory.APPAREL, "Linen shirt", 189_900, 2, deliveredDaysAgo(5));
    mugSet =
        item(2, ProductCategory.HOME_KITCHEN, "Ceramic mug set", 124_900, 1, deliveredDaysAgo(5));
    briefs = item(3, ProductCategory.INNERWEAR, "Cotton briefs", 59_900, 1, deliveredDaysAgo(5));
    earbuds =
        item(4, ProductCategory.ELECTRONICS, "Wireless earbuds", 499_900, 1, deliveredDaysAgo(11));

    CustomerOrder order =
        new CustomerOrder(
            UUID.randomUUID(), ORDER_NUMBER, EMAIL, "Aditi Sharma", NOW.minusSeconds(1_728_000));
    List.of(linenShirts, mugSet, briefs, earbuds).forEach(order::addItem);
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(ORDER_NUMBER, EMAIL))
        .thenReturn(Optional.of(order));
  }

  @Test
  void eligibleSelectionReturnsCountsAndEstimatedRefund() {
    ItemSelectionResponse response =
        service.validateSelection(request(select(linenShirts, 2), select(mugSet, 1)));

    assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
    assertThat(response.selectedItemCount()).isEqualTo(2);
    assertThat(response.selectedUnitCount()).isEqualTo(3);
    assertThat(response.estimatedRefundPaise()).isEqualTo(2 * 189_900 + 124_900);
  }

  @Test
  void selectedLineCarriesLineTotalAndLastReturnDate() {
    ItemSelectionResponse response = service.validateSelection(request(select(linenShirts, 2)));

    assertThat(response.items())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.itemId()).isEqualTo(linenShirts.getId());
              assertThat(line.quantity()).isEqualTo(2);
              assertThat(line.lineTotalPaise()).isEqualTo(379_800);
              assertThat(line.lastReturnDate()).isEqualTo(LocalDate.of(2026, 12, 15));
            });
  }

  @Test
  void partOfALineMayBeReturned() {
    ItemSelectionResponse response = service.validateSelection(request(select(linenShirts, 1)));

    assertThat(response.selectedUnitCount()).isEqualTo(1);
    assertThat(response.estimatedRefundPaise()).isEqualTo(189_900);
  }

  @Test
  void quantityAboveWhatWasOrderedIsRejected() {
    assertThatThrownBy(() -> service.validateSelection(request(select(linenShirts, 3))))
        .isInstanceOfSatisfying(
            ItemSelectionRejectedException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        linenShirts.getId() + ": You can return at most 2 of this item."));
  }

  @Test
  void nonReturnableCategoryIsRejectedWithItsReason() {
    assertThatThrownBy(() -> service.validateSelection(request(select(briefs, 1))))
        .isInstanceOfSatisfying(
            ItemSelectionRejectedException.class,
            ex ->
                assertThat(ex.getProblems())
                    .singleElement()
                    .asString()
                    .startsWith(briefs.getId().toString())
                    .contains("can't be returned"));
  }

  @Test
  void itemPastItsReturnWindowIsRejected() {
    assertThatThrownBy(() -> service.validateSelection(request(select(earbuds, 1))))
        .isInstanceOfSatisfying(
            ItemSelectionRejectedException.class,
            ex ->
                assertThat(ex.getProblems())
                    .singleElement()
                    .asString()
                    .contains("return window for Electronics closed on 19 Nov 2026"));
  }

  @Test
  void itemFromAnotherOrderIsRejected() {
    UUID foreignItemId = UUID.randomUUID();

    assertThatThrownBy(
            () -> service.validateSelection(request(new SelectedItemRequest(foreignItemId, 1))))
        .isInstanceOfSatisfying(
            ItemSelectionRejectedException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(foreignItemId + ": This item is not part of the order."));
  }

  @Test
  void sameItemSelectedTwiceIsRejected() {
    assertThatThrownBy(
            () ->
                service.validateSelection(request(select(linenShirts, 1), select(linenShirts, 1))))
        .isInstanceOfSatisfying(
            ItemSelectionRejectedException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        linenShirts.getId() + ": This item was selected more than once."));
  }

  @Test
  void everyProblemIsReportedTogetherAndValidLinesDoNotHideThem() {
    assertThatThrownBy(
            () ->
                service.validateSelection(
                    request(select(linenShirts, 1), select(briefs, 1), select(earbuds, 1))))
        .isInstanceOfSatisfying(
            ItemSelectionRejectedException.class, ex -> assertThat(ex.getProblems()).hasSize(2));
  }

  @Test
  void wrongEmailIsRefusedAsOrderNotFound() {
    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(anyString(), anyString()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.validateSelection(
                    new ItemSelectionRequest(
                        ORDER_NUMBER, "someone@example.com", List.of(select(linenShirts, 1)))))
        .isInstanceOf(OrderNotFoundException.class);
  }

  private static ItemSelectionRequest request(SelectedItemRequest... items) {
    return new ItemSelectionRequest(ORDER_NUMBER, EMAIL, List.of(items));
  }

  private static SelectedItemRequest select(OrderItem item, int quantity) {
    return new SelectedItemRequest(item.getId(), quantity);
  }

  private static OrderItem item(
      int lineNumber,
      ProductCategory category,
      String productName,
      long unitPricePaise,
      int quantity,
      Instant deliveredAt) {
    return new OrderItem(
        UUID.randomUUID(),
        lineNumber,
        "SKU-" + lineNumber,
        productName,
        null,
        category,
        unitPricePaise,
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
