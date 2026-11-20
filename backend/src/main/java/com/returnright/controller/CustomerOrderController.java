package com.returnright.controller;

import com.returnright.model.dto.request.ItemSelectionRequest;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.ItemSelectionResponse;
import com.returnright.model.dto.response.OrderLookupResponse;
import com.returnright.service.ItemSelectionService;
import com.returnright.service.OrderLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer wizard, steps 1 and 2: find the order, then choose the items to return. Both are POST so
 * the email stays out of URLs, browser history and access logs.
 */
@RestController
@RequestMapping("/api/v1/customer/orders")
public class CustomerOrderController {

  private final OrderLookupService orderLookupService;
  private final ItemSelectionService itemSelectionService;

  public CustomerOrderController(
      OrderLookupService orderLookupService, ItemSelectionService itemSelectionService) {
    this.orderLookupService = orderLookupService;
    this.itemSelectionService = itemSelectionService;
  }

  /**
   * Step 1, "Start a return".
   *
   * @return 200 with the order, 400 if a field is invalid, 404 if no order matches
   */
  @PostMapping("/lookup")
  public OrderLookupResponse lookupOrder(@Valid @RequestBody OrderLookupRequest request) {
    return orderLookupService.findOrder(request);
  }

  /**
   * Step 2, "Which items are you returning?", on Continue.
   *
   * @return 200 with the selection summary, 400 if the body is invalid, 404 if no order matches,
   *     422 if any selected item can't be returned as chosen
   */
  @PostMapping("/selection/validate")
  public ItemSelectionResponse validateSelection(@Valid @RequestBody ItemSelectionRequest request) {
    return itemSelectionService.validateSelection(request);
  }
}
