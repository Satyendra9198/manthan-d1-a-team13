package com.returnright.controller;

import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.OrderLookupResponse;
import com.returnright.service.OrderLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer wizard, step 1: find the order to return from. */
@RestController
@RequestMapping("/api/v1/customer/orders")
public class CustomerOrderController {

  private final OrderLookupService orderLookupService;

  public CustomerOrderController(OrderLookupService orderLookupService) {
    this.orderLookupService = orderLookupService;
  }

  /**
   * POST rather than GET so the email stays out of URLs, browser history and access logs.
   *
   * @return 200 with the order, 400 if a field is invalid, 404 if no order matches
   */
  @PostMapping("/lookup")
  public OrderLookupResponse lookupOrder(@Valid @RequestBody OrderLookupRequest request) {
    return orderLookupService.findOrder(request);
  }
}
