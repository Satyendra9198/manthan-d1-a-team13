package com.returnright.service.impl;

import com.returnright.exception.OrderNotFoundException;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.repository.CustomerOrderRepository;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Shared order access for the customer wizard. The customer has no login, so every step proves
 * ownership with the order number and the email the order was placed with.
 */
@Component
public class CustomerOrderReader {

  private final CustomerOrderRepository customerOrderRepository;

  public CustomerOrderReader(CustomerOrderRepository customerOrderRepository) {
    this.customerOrderRepository = customerOrderRepository;
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
   * Units of this line already on non-cancelled returns. Always 0 until return requests are
   * persisted; the create-return API will replace this with a query on its return lines.
   */
  public int quantityAlreadyReturned(OrderItem item) {
    return 0;
  }
}
