package com.returnright.service.impl;

import com.returnright.exception.OrderNotFoundException;
import com.returnright.model.domain.ReturnedQuantity;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.repository.CustomerOrderRepository;
import com.returnright.repository.ReturnItemRepository;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Shared order access for the customer wizard. The customer has no login, so every step proves
 * ownership with the order number and the email the order was placed with.
 */
@Component
public class CustomerOrderReader {

  private final CustomerOrderRepository customerOrderRepository;
  private final ReturnItemRepository returnItemRepository;

  public CustomerOrderReader(
      CustomerOrderRepository customerOrderRepository, ReturnItemRepository returnItemRepository) {
    this.customerOrderRepository = customerOrderRepository;
    this.returnItemRepository = returnItemRepository;
  }

  /**
   * Loads the order with its items. Case and surrounding spaces are ignored.
   *
   * @throws OrderNotFoundException if the number is unknown or the email does not match
   */
  public CustomerOrder findOwnedOrder(String orderNumber, String email) {
    return customerOrderRepository
        .findByOrderNumberAndCustomerEmail(
            orderNumber.trim().toUpperCase(Locale.ROOT), email.trim().toLowerCase(Locale.ROOT))
        .orElseThrow(OrderNotFoundException::new);
  }

  /**
   * Like {@link #findOwnedOrder} but also locks the order row until the transaction ends. Must be
   * called inside a transaction.
   */
  public CustomerOrder findOwnedOrderForUpdate(String orderNumber, String email) {
    CustomerOrder order = findOwnedOrder(orderNumber, email);
    customerOrderRepository.lockById(order.getId());
    return order;
  }

  /** Units per order item already on non-cancelled returns; items with none are absent. */
  public Map<UUID, Integer> quantitiesAlreadyReturned(CustomerOrder order) {
    return returnItemRepository.findReturnedQuantities(order.getId()).stream()
        .collect(
            Collectors.toMap(
                ReturnedQuantity::orderItemId, returned -> Math.toIntExact(returned.quantity())));
  }
}
