package com.returnright.service.impl;

import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.OrderItemResponse;
import com.returnright.model.dto.response.OrderLookupResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.service.EligibilityService;
import com.returnright.service.OrderLookupService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matches the order on number and email, then checks every line against the eligibility rules. */
@Service
public class OrderLookupServiceImpl implements OrderLookupService {

  private final CustomerOrderReader customerOrderReader;
  private final EligibilityService eligibilityService;

  public OrderLookupServiceImpl(
      CustomerOrderReader customerOrderReader, EligibilityService eligibilityService) {
    this.customerOrderReader = customerOrderReader;
    this.eligibilityService = eligibilityService;
  }

  @Override
  @Transactional(readOnly = true)
  public OrderLookupResponse findOrder(OrderLookupRequest request) {
    CustomerOrder order =
        customerOrderReader.findOwnedOrder(request.orderNumber(), request.email());

    Map<UUID, Integer> alreadyReturned = customerOrderReader.quantitiesAlreadyReturned(order);
    List<OrderItemResponse> items =
        order.getItems().stream()
            .map(item -> toItemResponse(item, alreadyReturned.getOrDefault(item.getId(), 0)))
            .toList();
    int returnableItemCount = (int) items.stream().filter(OrderItemResponse::returnable).count();

    return new OrderLookupResponse(
        order.getId(), order.getOrderNumber(), order.getPlacedAt(), returnableItemCount, items);
  }

  private OrderItemResponse toItemResponse(OrderItem item, int alreadyReturned) {
    int remaining = item.getQuantity() - alreadyReturned;
    EligibilityResult eligibility =
        eligibilityService.evaluate(
            new ItemEligibilityInput(
                item.getCategory(),
                item.getDeliveredAt(),
                item.getQuantity(),
                alreadyReturned,
                remaining));

    return new OrderItemResponse(
        item.getId(),
        item.getSku(),
        item.getProductName(),
        item.getVariant(),
        item.getCategory(),
        item.getUnitPricePaise(),
        item.getQuantity(),
        eligibility.eligible() ? remaining : 0,
        item.getDeliveredAt(),
        eligibility.eligible(),
        eligibility.reason(),
        eligibility.message(),
        eligibility.lastReturnDate());
  }
}
