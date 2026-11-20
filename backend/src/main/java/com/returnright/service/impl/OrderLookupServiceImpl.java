package com.returnright.service.impl;

import com.returnright.exception.OrderNotFoundException;
import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.OrderItemResponse;
import com.returnright.model.dto.response.OrderLookupResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.repository.CustomerOrderRepository;
import com.returnright.service.EligibilityService;
import com.returnright.service.OrderLookupService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matches the order on number and email, then checks every line against the eligibility rules. */
@Service
public class OrderLookupServiceImpl implements OrderLookupService {

  private final CustomerOrderRepository customerOrderRepository;
  private final EligibilityService eligibilityService;

  public OrderLookupServiceImpl(
      CustomerOrderRepository customerOrderRepository, EligibilityService eligibilityService) {
    this.customerOrderRepository = customerOrderRepository;
    this.eligibilityService = eligibilityService;
  }

  @Override
  @Transactional(readOnly = true)
  public OrderLookupResponse findOrder(OrderLookupRequest request) {
    String orderNumber = request.orderNumber().trim().toUpperCase(Locale.ROOT);
    String email = request.email().trim().toLowerCase(Locale.ROOT);

    CustomerOrder order =
        customerOrderRepository
            .findByOrderNumberAndCustomerEmail(orderNumber, email)
            .orElseThrow(OrderNotFoundException::new);

    List<OrderItemResponse> items = order.getItems().stream().map(this::toItemResponse).toList();
    int returnableItemCount = (int) items.stream().filter(OrderItemResponse::returnable).count();

    return new OrderLookupResponse(
        order.getId(), order.getOrderNumber(), order.getPlacedAt(), returnableItemCount, items);
  }

  private OrderItemResponse toItemResponse(OrderItem item) {
    int alreadyReturned = quantityAlreadyReturned(item);
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

  /**
   * Units of this line already on non-cancelled returns. Always 0 until return requests are
   * persisted; the create-return API will replace this with a query on its return lines.
   */
  private int quantityAlreadyReturned(OrderItem item) {
    return 0;
  }
}
