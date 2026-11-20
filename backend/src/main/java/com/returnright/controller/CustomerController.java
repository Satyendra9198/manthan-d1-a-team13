package com.returnright.controller;

import com.returnright.model.dto.request.CreateReturnRequest;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.LabelResponse;
import com.returnright.model.dto.response.OrderResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.service.CustomerReturnService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer self-service. No login: the order number and email (in the body, or in the {@code
 * X-Order-Number} and {@code X-Customer-Email} headers) identify the customer.
 */
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

  static final String ORDER_NUMBER_HEADER = "X-Order-Number";
  static final String EMAIL_HEADER = "X-Customer-Email";

  private final CustomerReturnService customerReturnService;

  public CustomerController(CustomerReturnService customerReturnService) {
    this.customerReturnService = customerReturnService;
  }

  /** Finds the order and shows, per item, whether it can be returned and why not. */
  @PostMapping("/orders/lookup")
  public OrderResponse lookupOrder(@Valid @RequestBody OrderLookupRequest request) {
    return customerReturnService.lookupOrder(request);
  }

  /** Requested → Approved (with label) unless the customer is flagged for review. */
  @PostMapping("/returns")
  @ResponseStatus(HttpStatus.CREATED)
  public ReturnResponse createReturn(@Valid @RequestBody CreateReturnRequest request) {
    return customerReturnService.createReturn(request);
  }

  @GetMapping("/returns/{returnId}")
  public ReturnResponse getReturn(
      @PathVariable UUID returnId,
      @RequestHeader(ORDER_NUMBER_HEADER) String orderNumber,
      @RequestHeader(EMAIL_HEADER) String email) {
    return customerReturnService.getReturn(returnId, orderNumber, email);
  }

  @GetMapping("/returns/{returnId}/label")
  public LabelResponse getLabel(
      @PathVariable UUID returnId,
      @RequestHeader(ORDER_NUMBER_HEADER) String orderNumber,
      @RequestHeader(EMAIL_HEADER) String email) {
    return customerReturnService.getLabel(returnId, orderNumber, email);
  }

  /** Allowed while Requested or Approved. */
  @PostMapping("/returns/{returnId}/cancel")
  public ReturnResponse cancelReturn(
      @PathVariable UUID returnId,
      @RequestHeader(ORDER_NUMBER_HEADER) String orderNumber,
      @RequestHeader(EMAIL_HEADER) String email) {
    return customerReturnService.cancelReturn(returnId, orderNumber, email);
  }

  /** Approved → In transit, once the customer has dropped off the parcel. */
  @PostMapping("/returns/{returnId}/ship")
  public ReturnResponse markShipped(
      @PathVariable UUID returnId,
      @RequestHeader(ORDER_NUMBER_HEADER) String orderNumber,
      @RequestHeader(EMAIL_HEADER) String email) {
    return customerReturnService.markShipped(returnId, orderNumber, email);
  }
}
